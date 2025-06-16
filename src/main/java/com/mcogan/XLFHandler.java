package com.mcogan;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;


public class XLFHandler  extends DefaultHandler {
    private static final Logger LOG = Logger.getLogger(XLFHandler.class.toString());
    private StringBuilder transUnitId=null;
    private StringBuilder translationText=null;

    public HashMap<String, String> getTranslationMap() {
        return translationMap;
    }

    public void setTranslationMap(HashMap<String, String> translationMap) {
        this.translationMap = translationMap;
    }

    private HashMap<String,String> translationMap=new HashMap<>();
    private Boolean processingTargetUnit=false;
    private static ResourceBundle messages=null;
    private static String usingLocale=null;
    static {
        try {
            Locale newLocale = new Locale("fr","FR");
            usingLocale=newLocale.toString();
            messages = ResourceBundle.getBundle("messages", newLocale);
        }catch(Exception e){
            System.err.println("Erreur lors de l'initialisation des messages de l'application. Veuillez vérifier" +
                    " les autorisations du fichier messages.properties.");
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        final String METHOD_NAME="startElement";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        switch (qName) {
            case "trans-unit":
                transUnitId=new StringBuilder();
                transUnitId.append(attributes.getValue("id"));

                translationText=new StringBuilder();

                System.out.println(" in sax event for start element");
                break;
            case "target":
                processingTargetUnit=true;
                break;
            default:
                System.out.println("qname is " + qName);
                processingTargetUnit=false;
                break;
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        final String METHOD_NAME="characters";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        StringBuilder content=new StringBuilder();
        content.append(ch,start,length);
        System.out.println("Processing characters event for "  +content);
        if(processingTargetUnit) {
            translationText.append(ch,start,length);
            System.out.println("translationText = " + translationText.toString());
            processingTargetUnit=false;
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        final String METHOD_NAME="endElement";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        switch (qName) {
            case "target":
                translationMap.put(transUnitId.toString(), translationText.toString());
                System.out.println("adding id of "+ transUnitId + " : " + translationText.toString());
                break;

            default:
                System.out.println("qname is " + qName);
                break;
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
}
