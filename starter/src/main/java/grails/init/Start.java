package grails.init;

import grails.proxy.SystemPropertiesAuthenticator;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Start {

    private static final String PROJECT_NAME = "grails-wrapper";
    private static final String BASE_URL = "http://repo.grails.org/grails/core/org/grails/" + PROJECT_NAME;
    private static final File WRAPPER_DIR = new File(System.getProperty("user.home") + "/.grails/wrapper");
    private static final File NO_VERSION_JAR = new File(WRAPPER_DIR, PROJECT_NAME + ".jar");

    private static String getVersion() throws SAXException, ParserConfigurationException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            FindReleaseHandler findReleaseHandler = new FindReleaseHandler();
            saxParser.parse(new URL(BASE_URL + "/maven-metadata.xml").openStream(), findReleaseHandler);

            return findReleaseHandler.getVersion();
        } catch (Exception e) {
            if (!NO_VERSION_JAR.exists()) {
                System.out.println("You must be connected to the internet the first time you use the Grails wrapper");
                e.printStackTrace();
                System.exit(1);
            }
            return null;
        }
    }


    private static boolean updateJar(String version) {

        boolean success = false;

        final String jarFileName = PROJECT_NAME + "-" + version;
        final String jarFileExtension = ".jar";

        WRAPPER_DIR.mkdirs();

        try {
            File dowloadedJar = File.createTempFile(jarFileName, jarFileExtension);

            URL website = new URL(BASE_URL + "/" + version + "/" + jarFileName + jarFileExtension);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(dowloadedJar);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();

            Files.move(dowloadedJar.getAbsoluteFile().toPath(), NO_VERSION_JAR.getAbsoluteFile().toPath(), REPLACE_EXISTING);

            success = true;
        } catch(Exception e) {
            System.out.println("There was an error downloading the wrapper jar");
            e.printStackTrace();
        }

        return success;
    }

    public static void main(String[] args) {
        Authenticator.setDefault(new SystemPropertiesAuthenticator());

        try {
            String version = getVersion();

            // At this point either we have a version or the NO_VERSION_JAR exists, or both.
            if (version != null) {

                Properties props = new Properties();
                String existingVersion;

                File gradleProperties = new File("gradle.properties");

                if (!gradleProperties.exists()) {
                    System.out.println("Could not determine grails wrapper version due to missing gradle.properties file. Using existing jar");
                } else {
                    props.load(new FileInputStream(gradleProperties));

                    final String GRAILS_WRAPPER_VERSION = "grailsWrapperVersion";

                    existingVersion = props.getProperty(GRAILS_WRAPPER_VERSION, null);

                    if (existingVersion == null || !existingVersion.equals(version) || !NO_VERSION_JAR.exists()) {

                        if (updateJar(version)) {

                            FileOutputStream outputStream = null;
                            try {
                                props.put(GRAILS_WRAPPER_VERSION, version);
                                outputStream = new FileOutputStream(gradleProperties);
                                props.store(outputStream, null);
                            } catch (IOException e) {
                                System.out.println("Could not update grails wrapper version");
                                e.printStackTrace();
                            } finally {
                                if (outputStream != null) {
                                    outputStream.close();
                                }
                            }
                        }
                    }
                }
            }

            URLClassLoader child = new URLClassLoader(new URL[] {NO_VERSION_JAR.toURI().toURL()});
            Class classToLoad = Class.forName("grails.init.RunCommand", true, child);
            Method main = classToLoad.getMethod("main", String[].class);
            main.invoke(null, (Object)args);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
