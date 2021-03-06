package com.vaadin.tests.tb3;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;

import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.vaadin.testbench.annotations.BrowserFactory;
import com.vaadin.testbench.annotations.RunLocally;
import com.vaadin.testbench.annotations.RunOnHub;
import com.vaadin.testbench.parallel.Browser;
import com.vaadin.testbench.parallel.BrowserUtil;

/**
 * Provides values for parameters which depend on where the test is run.
 * Parameters should be configured in work/eclipse-run-selected-test.properties.
 * A template is available in uitest/.
 *
 * @author Vaadin Ltd
 */
@RunOnHub("tb3-hub.intra.itmill.com")
@BrowserFactory(VaadinBrowserFactory.class)
public abstract class PrivateTB3Configuration extends ScreenshotTB3Test {
    /**
     *
     */
    public static final String SCREENSHOT_DIRECTORY = "com.vaadin.testbench.screenshot.directory";
    private static final String HOSTNAME_PROPERTY = "com.vaadin.testbench.deployment.hostname";
    private static final String RUN_LOCALLY_PROPERTY = "com.vaadin.testbench.runLocally";
    private static final String ALLOW_RUN_LOCALLY_PROPERTY = "com.vaadin.testbench.allowRunLocally";
    private static final String PORT_PROPERTY = "com.vaadin.testbench.deployment.port";
    private static final String DEPLOYMENT_PROPERTY = "com.vaadin.testbench.deployment.url";
    private static final String HUB_URL = "com.vaadin.testbench.hub.url";
    private static final Properties properties = new Properties();
    private static final File propertiesFile = new File("../work",
            "eclipse-run-selected-test.properties");
    private static final String FIREFOX_PATH = "firefox.path";
    private static final String PHANTOMJS_PATH = "phantomjs.binary.path";

    static {
        if (propertiesFile.exists()) {
            try {
                properties.load(new FileInputStream(propertiesFile));
                if (properties.containsKey(RUN_LOCALLY_PROPERTY)) {
                    System.setProperty("useLocalWebDriver", "true");
                    DesiredCapabilities localBrowser = getRunLocallyCapabilities();
                    System.setProperty("browsers.include",
                            localBrowser.getBrowserName()
                                    + localBrowser.getVersion());
                }
                if (properties.containsKey(FIREFOX_PATH)) {
                    System.setProperty(FIREFOX_PATH,
                            properties.getProperty(FIREFOX_PATH));
                }
                if (properties.containsKey(PHANTOMJS_PATH)) {
                    System.setProperty(PHANTOMJS_PATH,
                            properties.getProperty(PHANTOMJS_PATH));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setup() throws Exception {
        String allowRunLocally = getProperty(ALLOW_RUN_LOCALLY_PROPERTY);
        if ((allowRunLocally == null || !allowRunLocally.equals("" + true))
                && getClass().getAnnotation(RunLocally.class) != null) {
            fail("@RunLocally annotation is not allowed by default in framework tests. "
                    + "See file uitest/eclipse-run-selected-test.properties for more information.");
        }

        super.setup();
    }

    @Override
    public void setDesiredCapabilities(
            DesiredCapabilities desiredCapabilities) {
        super.setDesiredCapabilities(desiredCapabilities);

        if (BrowserUtil.isIE(desiredCapabilities)) {
            if (requireWindowFocusForIE()) {
                desiredCapabilities.setCapability(
                        InternetExplorerDriver.REQUIRE_WINDOW_FOCUS, true);
            }
            if (!usePersistentHoverForIE()) {
                desiredCapabilities.setCapability(
                        InternetExplorerDriver.ENABLE_PERSISTENT_HOVERING,
                        false);
            }
            if (!useNativeEventsForIE()) {
                desiredCapabilities.setCapability(
                        InternetExplorerDriver.NATIVE_EVENTS, false);
            }
        }

        desiredCapabilities.setCapability("project", "Vaadin Framework");
        desiredCapabilities.setCapability("build", String.format("%s / %s",
                getDeploymentHostname(), Calendar.getInstance().getTime()));
        desiredCapabilities.setCapability("name", String.format("%s.%s",
                getClass().getCanonicalName(), testName.getMethodName()));
    }

    protected static DesiredCapabilities getRunLocallyCapabilities() {
        VaadinBrowserFactory factory = new VaadinBrowserFactory();
        try {
            return factory.create(
                    Browser.valueOf(properties.getProperty(RUN_LOCALLY_PROPERTY)
                            .toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Falling back to FireFox");
        }
        return factory.create(Browser.FIREFOX);
    }

    protected static String getProperty(String name) {
        String property = properties.getProperty(name);
        if (property == null) {
            property = System.getProperty(name);
        }

        return property;
    }

    @Override
    protected String getScreenshotDirectory() {
        String screenshotDirectory = getProperty(SCREENSHOT_DIRECTORY);
        if (screenshotDirectory == null) {
            throw new RuntimeException("No screenshot directory defined. Use -D"
                    + SCREENSHOT_DIRECTORY + "=<path>");
        }
        return screenshotDirectory;
    }

    @Override
    protected String getHubURL() {
        String hubUrl = getProperty(HUB_URL);
        if (hubUrl == null || hubUrl.trim().isEmpty()) {
            return super.getHubURL();
        }

        return hubUrl;
    }

    @Override
    protected String getBaseURL() {
        if (isRunLocally()) {
            return "http://localhost:8888";
        }
        String url = getProperty(DEPLOYMENT_PROPERTY);
        if (url == null || url.trim().isEmpty()) {
            return super.getBaseURL();
        }
        return url;
    }

    @Override
    protected String getDeploymentHostname() {
        if (isRunLocally()) {
            return "localhost";
        }
        return getConfiguredDeploymentHostname();
    }

    protected boolean isRunLocally() {
        if (properties.containsKey(RUN_LOCALLY_PROPERTY)) {
            return true;
        }

        if (properties.containsKey(ALLOW_RUN_LOCALLY_PROPERTY)
                && properties.get(ALLOW_RUN_LOCALLY_PROPERTY).equals("true")
                && getClass().getAnnotation(RunLocally.class) != null) {
            return true;
        }

        return false;
    }

    /**
     * Gets the hostname that tests are configured to use.
     *
     * @return the host name configuration value
     */
    public static String getConfiguredDeploymentHostname() {
        String hostName = getProperty(HOSTNAME_PROPERTY);

        if (hostName == null || hostName.isEmpty()) {
            hostName = findAutoHostname();
        }

        return hostName;
    }

    @Override
    protected int getDeploymentPort() {
        return getConfiguredDeploymentPort();
    }

    /**
     * Gets the port that tests are configured to use.
     *
     * @return the port configuration value
     */
    public static int getConfiguredDeploymentPort() {
        String portString = getProperty(PORT_PROPERTY);

        int port = 8888;
        if (portString != null && !portString.isEmpty()) {
            port = Integer.parseInt(portString);
        }

        return port;
    }

    /**
     * Tries to automatically determine the IP address of the machine the test
     * is running on.
     *
     * @return An IP address of one of the network interfaces in the machine.
     * @throws RuntimeException
     *             if there was an error or no IP was found
     */
    private static String findAutoHostname() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nwInterface = interfaces.nextElement();
                if (!nwInterface.isUp() || nwInterface.isLoopback()
                        || nwInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nwInterface
                        .getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress()) {
                        continue;
                    }
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException("Could not enumerate ");
        }

        throw new RuntimeException(
                "No compatible (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) ip address found.");
    }
}
