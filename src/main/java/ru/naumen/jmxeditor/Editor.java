package ru.naumen.jmxeditor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Editor extends DefaultHandler {

	public static void main(String argv[]) {
		
		if (argv.length != 1) {
			System.err.println("Usage: cmd filename");
			System.exit(1);
		}
		
		DefaultHandler handler = new Editor(); 
		SAXParserFactory factory = SAXParserFactory.newInstance();

		try {
			out = new OutputStreamWriter(System.out, "UTF8");
			
			SAXParser saxParser = factory.newSAXParser();
		    saxParser.parse( new File(argv[0]), handler );
		    
		} catch (Throwable t) {
			t.printStackTrace();
		}
		
		System.exit(0);
	}

	static private Writer out;
	private static Pattern pattern = Pattern.compile("(ru\\.naumen\\..*?)[/|\\s]");
	
	private StringBuffer textBuffer;
	
	private StringBuffer samplerBuffer;
	private String testName;
	private Set<String> classNames;
	
	private int counter = 0;
	
	public void characters(char buf[], int offset, int len) throws SAXException {
		String s = new String(buf, offset, len);
		if (textBuffer == null) {
			textBuffer = new StringBuffer(s);
		} else {
			textBuffer.append(s);
		}
	}
	
	private void echoText() throws SAXException {
		if (textBuffer == null)
			return;
		String s = textBuffer.toString();
		
		if(samplerBuffer != null && classNames != null) {
			Matcher matcher = pattern.matcher(s);
			while (matcher.find()) {
				String group = matcher.group(1);
				String[] split = StringUtils.split(group, '.');
				String simpleClassName = split[split.length - 1];
				if(StringUtils.endsWith(simpleClassName, "Action")) {
					classNames.add(simpleClassName);
				}
			}
		}
		
		emit(StringEscapeUtils.escapeXml11(s));
		textBuffer = null;
	}
	
	public void startElement(String namespaceURI, String sName,
			String qName, Attributes attrs) throws SAXException {

		echoText();
		
		String eName = sName; // element name
		if ("".equals(eName)) {
			eName = qName; // not namespace-aware
		}
		
		if("HTTPSamplerProxy".equals(eName)) {
			startBuffer();
		}

		emit("<" + eName);
		if (attrs != null) {
			for (int i = 0; i < attrs.getLength(); i++) {
				String aName = attrs.getLocalName(i); // Attr name
				if ("".equals(aName))
					aName = attrs.getQName(i);
				
				emit(" ");
				if("HTTPSamplerProxy".equals(eName) && "testname".equals(aName)) {
					this.testName = attrs.getValue(i);
				}
				emit(aName + "=\"" + attrs.getValue(i) + "\"");
			}
		}
		
		emit(">");
	}

	private void startBuffer() throws SAXException {
		counter++;
		if(samplerBuffer != null) {
			flushSection();
		}
		samplerBuffer = new StringBuffer();
		classNames = new HashSet<String>();
	}

	private void flushSection() throws SAXException {
		String section = samplerBuffer.toString();
		
		if(classNames != null && !classNames.isEmpty()) {
			String search = "testname=\"" + testName + "\"";
			String newName = "testname=\"" + counter + " /" + StringUtils.join(classNames, ",") + "\"";
			section = StringUtils.replaceOnce(section, search, newName);
		}
		
		testName = null;
		samplerBuffer = null;
		classNames = null;
		emit(section);
	}

	private void stopBuffer() throws SAXException {
		flushSection();
	}

	public void endElement(String namespaceURI, String sName, 
			String qName ) throws SAXException {
		echoText();
		String eName = sName; // element name
		if ("".equals(eName))
			eName = qName; // not namespace-aware
		
		emit("</" + eName + ">");

		if("HTTPSamplerProxy".equals(eName)) {
			stopBuffer();
		}
	}
	

	public void startDocument() throws SAXException {
		emit("<?xml version='1.0' encoding='UTF-8'?>");
		nl();
	}

	public void endDocument() throws SAXException {
		try {
			nl();
			out.flush();
		} catch (IOException e) {
			throw new SAXException("I/O error", e);
		}
	}
	
	private void emit(String s) throws SAXException {
		try {
			if(samplerBuffer != null) {
				samplerBuffer.append(s);
			}
			else {
				out.write(s);
				out.flush();
			}
		} catch (IOException e) {
			throw new SAXException("I/O error", e);
		}
	}
	
	private void nl() throws SAXException {
		emit(System.getProperty("line.separator"));
	}
}
