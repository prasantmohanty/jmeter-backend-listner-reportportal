package io.github.prasantmohanty.jmeter.backendlistener.junit.transform;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DomXmlJUnitReportWriter implements Closeable {
    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    private static final Logger logger = LoggerFactory.getLogger(DomXmlJUnitReportWriter.class);


    private final String fileName;
    private final Document doc;
    private Element rootElement;

    private final String testSuiteName;

    private int testsCount;
    private int failures;
    private int errors;
    private int skipped;

    public DomXmlJUnitReportWriter(File file, String testSuiteName) {
        this(file.getAbsolutePath(), testSuiteName);
    }
    public DomXmlJUnitReportWriter(String fileName, String testSuiteName) {
        this.fileName = fileName;
        this.testSuiteName = testSuiteName;
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            logger.error("Failed to initialize XML writer", e);
            throw new IllegalStateException("fail to init XML writer", e);

        }
        doc = documentBuilder.newDocument();
        rootElement = doc.createElement("testsuite");
        rootElement.setAttribute("name", testSuiteName);
        doc.appendChild(rootElement);
    }


    public void write(JtlRecord jtlRecord) {
        testsCount++;

        Element testCase = doc.createElement("testcase");
        testCase.setAttribute("classname", testSuiteName);
        testCase.setAttribute("name", jtlRecord.getLabel());
        if (!jtlRecord.isSuccess()) {
            Element failureDetails;
            String failureMessage;
            if (jtlRecord.getFailureMessage() != null && !jtlRecord.getFailureMessage().isEmpty()) {
                failures++;
                failureDetails = doc.createElement("failure");
                failureMessage = jtlRecord.getFailureMessage();
            } else {
                errors++;
                failureDetails = doc.createElement("error");
                failureMessage = jtlRecord.getResponseMessage();
            }
            failureDetails.setAttribute("message", failureMessage);
            testCase.appendChild(failureDetails);
         }
        // Add request/response details into system-out so they are available in the JUnit XML import
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ResponseCode: ").append(safe(jtlRecord.getResponseCode())).append("\n");
            sb.append("ResponseMessage: ").append(safe(jtlRecord.getResponseMessage())).append("\n\n");
            sb.append("Request Headers:\n").append(safe(jtlRecord.getRequestHeaders())).append("\n\n");
            sb.append("Request Body:\n").append(safe(jtlRecord.getRequestBody())).append("\n\n");
            sb.append("Response Headers:\n").append(safe(jtlRecord.getResponseHeaders())).append("\n\n");
            sb.append("Response Body:\n").append(safe(jtlRecord.getResponseBody())).append("\n");

            Element systemOut = doc.createElement("system-out");
            systemOut.appendChild(doc.createCDATASection(sb.toString()));
            testCase.appendChild(systemOut);
        } catch (Exception e) {
            logger.debug("Failed to attach request/response to junit testcase for {}", jtlRecord.getLabel(), e);
        }
        rootElement.appendChild(testCase);
        logger.debug("Written test case: {}", jtlRecord.getLabel());
        logger.debug("Appended the test case to rootElement" );
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }


    @Override
    public void close() throws IOException {
        try {
            rootElement.setAttribute("tests", Integer.toString(testsCount));
            rootElement.setAttribute("failures", Integer.toString(failures));
            rootElement.setAttribute("errors", Integer.toString(errors));
            rootElement.setAttribute("skipped", Integer.toString(skipped));
            flush();
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

    private void flush() throws TransformerException, IOException {
        // output DOM XML to console
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        DOMSource source = new DOMSource(doc);
        StreamResult streamResult;
        if (fileName != null) {
            streamResult = new StreamResult(new BufferedWriter(new FileWriter(new File(fileName))));
        } else {
            streamResult = new StreamResult(System.out);
        }
        transformer.transform(source, streamResult);

    }
}
