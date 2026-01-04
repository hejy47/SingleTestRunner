package apr.testrunner;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.testng.TestNG;
import org.testng.TestListenerAdapter;
import org.testng.ITestResult;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

public class SingleTestRunner {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: SingleTestRunner <className>#<methodName>");
            System.exit(1);
        }

        String target = args[0];
        String[] parts = target.split("#");
        if (parts.length != 2) {
            System.err.println("Invalid format. Expected <className>#<methodName>");
            System.exit(1);
        }

        String className = parts[0];
        String methodName = parts[1];

        Class<?> testClass = Class.forName(className);
        Framework framework = detectFramework(testClass, methodName);

        System.out.println("Detected framework: " + framework);
        System.out.println("Test run: " + target);

        boolean success = false;
        switch (framework) {
            case JUNIT4:
                success = runJUnit4(testClass, methodName);
                break;
            case JUNIT5:
                success = runJUnit5(className, methodName);
                break;
            case TESTNG:
                success = runTestNG(testClass, methodName);
                break;
            default:
                System.err.println("Unknown framework");
                System.exit(1);
        }

        System.exit(success ? 0 : 1);
    }

    private enum Framework {
        JUNIT4, JUNIT5, TESTNG, UNKNOWN
    }

    private static Framework detectFramework(Class<?> testClass, String methodName) {
        try {
            Method method = null;
            for (Method m : testClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    method = m;
                    break;
                }
            }

            if (method != null) {
                for (Annotation ann : method.getAnnotations()) {
                    String name = ann.annotationType().getName();
                    if (name.equals("org.junit.Test")) return Framework.JUNIT4;
                    if (name.equals("org.junit.jupiter.api.Test") || name.equals("org.junit.jupiter.api.ParameterizedTest")) return Framework.JUNIT5;
                    if (name.equals("org.testng.annotations.Test")) return Framework.TESTNG;
                }
            }

            // Check class level for TestNG
            for (Annotation ann : testClass.getAnnotations()) {
                String name = ann.annotationType().getName();
                if (name.equals("org.testng.annotations.Test")) return Framework.TESTNG;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return Framework.UNKNOWN;
    }

    private static boolean runJUnit4(Class<?> testClass, String methodName) {
        Request request = Request.method(testClass, methodName);
        Result result = new JUnitCore().run(request);
        
        if (!result.getFailures().isEmpty()) {
            System.out.println("Failures:");
            for (Failure failure : result.getFailures()) {
                System.out.println(" - Test: " + failure.getDescription().getDisplayName());
                System.out.println("   Message: " + failure.getMessage());
                System.out.println("   Trace:");
                for (String line : failure.getTrace().split("\\r?\\n")) {
                    System.out.println("     " + line);
                }
            }
        } else {
            System.out.println("Success.");
        }
        return result.wasSuccessful();
    }

    private static boolean runJUnit5(String className, String methodName) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectMethod(className, methodName))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        
        if (summary.getTestsFailedCount() > 0) {
            System.out.println("Failures:");
            for (TestExecutionSummary.Failure failure : summary.getFailures()) {
                System.out.println(" - Test: " + failure.getTestIdentifier().getDisplayName());
                System.out.println("   Message: " + failure.getException().getMessage());
                System.out.println("   Trace:");
                StringWriter sw = new StringWriter();
                failure.getException().printStackTrace(new PrintWriter(sw));
                for (String line : sw.toString().split("\\r?\\n")) {
                    System.out.println("     " + line);
                }
            }
        } else {
            if (summary.getTestsStartedCount() > 0) {
                System.out.println("Success.");
            } else {
                System.out.println("No tests found or started.");
            }
        }

        return summary.getTestsFailedCount() == 0 && summary.getTestsStartedCount() > 0;
    }

    private static boolean runTestNG(Class<?> testClass, String methodName) {
        TestNG testng = new TestNG();
        TestListenerAdapter tla = new TestListenerAdapter();
        testng.addListener(tla);
        testng.setUseDefaultListeners(false); 
        testng.setVerbose(0); 
        
        XmlSuite suite = new XmlSuite();
        suite.setName("SingleSuite");
        
        XmlTest test = new XmlTest(suite);
        test.setName("SingleTest");
        
        XmlClass xmlClass = new XmlClass(testClass);
        XmlInclude include = new XmlInclude(methodName);
        xmlClass.setIncludedMethods(Collections.singletonList(include));
        
        test.setXmlClasses(Collections.singletonList(xmlClass));
        
        testng.setXmlSuites(Collections.singletonList(suite));
        testng.run();
        
        if (testng.hasFailure()) {
             System.out.println("Failures:");
             for (ITestResult result : tla.getFailedTests()) {
                 System.out.println(" - Test: " + result.getName());
                 System.out.println("   Message: " + result.getThrowable().getMessage());
                 System.out.println("   Trace:");
                 StringWriter sw = new StringWriter();
                 result.getThrowable().printStackTrace(new PrintWriter(sw));
                 for (String line : sw.toString().split("\\r?\\n")) {
                     System.out.println("     " + line);
                 }
             }
        } else {
             System.out.println("Success.");
        }
        
        return !testng.hasFailure();
    }
}