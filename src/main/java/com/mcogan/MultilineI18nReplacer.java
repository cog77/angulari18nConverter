package com.mcogan;
import org.apache.commons.text.StringEscapeUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MultilineI18nReplacer {
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
    private static final Logger LOG = Logger.getLogger(MultilineI18nReplacer.class.toString());
    /**
     * Process the accumulated tag block and replace the content after tag > with the translation
     */
    private static String processTagBlock(List<String> tagBlockLines,
                                          Map<String, String> translations,
                                          String currentTagName) {
        final String METHOD_NAME="processTagBlock";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        String block = String.join("\n", tagBlockLines);
        // 1. Extract i18n key using simple index approach
        int i18nIndex = block.indexOf("i18n=\"@@");
        if (i18nIndex == -1){
            return block; // no i18n, return as-is
        }
//this is from the start that we identified so this is robust i.e. the 8 represents i18n="@@ (len = 8)
        int keyStart = i18nIndex + 8; // points at first @
        //next speechmark following on
        int keyEnd = block.indexOf("\"", keyStart);
        if (keyEnd == -1) {
            System.err.println(messages.getString(MessageKeys.I18N_MAL_FOREMEE));
            return block; // malformed, no ending quote
        }
        //this works as even with the description the key is still all inside speechmarks
        String key = block.substring(keyStart, keyEnd);
        // 2. Find the closing > of the opening tag this is still the i18n tag so we are good
        int tagEndIndex = block.indexOf(">", keyEnd);
        if (tagEndIndex == -1) {
            System.err.println(messages.getString(MessageKeys.AUCUNE_BALISE_I18N_FERMEE));
            return block; // malformed, no closing >
        }
        // 3. Find the start of the next tag after tagEndIndex
        int nextTagIndex = block.indexOf("</"+currentTagName, tagEndIndex + 1);
        // 4. Extract current text content between > and next <
        String oldContent = block.substring(tagEndIndex + 1, nextTagIndex);
        // 5. Lookup translation, fallback to old content if not found
        String translated = translations.getOrDefault(key, oldContent);
        if(translated.equals(oldContent)){
            System.err.println("Erreur lors de la traduction de " + key + " pour " + currentTagName +
                    " ce qui risque de provoquer des problèmes en cascade.");
        }
        // 6. Rebuild string replacing old content with translated text
        StringBuilder result = new StringBuilder();
        result.append(block, 0, tagEndIndex + 1);
        result.append(translated);
        result.append(block.substring(nextTagIndex));
        //now remove the i18n text need to repeat the routine the have changed as the text has
        // been translated!!!
        String finalizedResult=result.substring(0,i18nIndex-1)+result.substring(keyEnd+1);
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return finalizedResult;
    }
    public static String replaceXEquivText(String content) {
        final String METHOD_NAME="replaceXEquivText";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        String equivText=new String(content);
        //process Bold tags first
        equivText=processBoldXTags(content);
        //process italics
        equivText=processItalicXTags(equivText);
        //process links (can't do this here) hrefs are not populated move earlier on
  //      equivText=processLinkXTags(equivText);
        equivText=processLineBreaks(equivText);
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return equivText;
    }
    public static String processLineBreaks(String content){
        final String METHOD_NAME="processLineBreaks";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        final String tagToReplace = "<x id=\"LINE_BREAK\"/>"; //<x id="LINE_BREAK"/>
        final String LONGER_TAG="<x id=\"LINE_BREAK\"";
        final String replacement = "<br/>";
        // Perform the replacement
        String output = content.replace(tagToReplace, replacement);
        StringBuilder outputBuilder=new StringBuilder();
        int textPointer=0;
        //get rid of the ones that have more content in them
        if(output.contains(LONGER_TAG)){
            boolean processLineBreaks=true;
            while(processLineBreaks){
                int lineBreakStartPos=output.indexOf(LONGER_TAG,textPointer);
                if(lineBreakStartPos!=-1) {
                    int lineBreakEndPos = output.indexOf("/>", lineBreakStartPos)+2;
                    String textToReplace = output.substring(lineBreakStartPos, lineBreakEndPos);
                    outputBuilder.append(output.substring(textPointer, lineBreakStartPos));
                    outputBuilder.append(replacement);
                    textPointer=lineBreakEndPos;
                }else{
                    outputBuilder.append(output.substring(textPointer));
                    processLineBreaks=false;
                }
            }
            LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
            return outputBuilder.toString();
        }else {
            LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
            return output;
        }
    }
    private static String processBoldXTags(String content){
        final String METHOD_NAME="processBoldXTags";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        StringBuilder equivText=new StringBuilder();
        equivText.append(content);
        //standard statics representing bold tags
        final String BOLD_START_TAG="<x id=\"START_TAG_STRONG\"";
        //                                                                                      <strong>
        final String BOLD_END_TAG="<x id=\"CLOSE_TAG_STRONG\"";
        //                                                                                      </strong>
        //link tags
        final String LINK_START_TAG="<x id=\"START_LINK\" ctype=\"x-a\" " +
                "equiv-text=\"&lt;a class=&quot;nav-link active&quot; id=&quot;benefits-tab&quot; " +
                "data-bs-toggle=&quot;tab&quot; href=&quot;#benefits&quot; role=&quot;tab&quot; " +
                "aria-controls=&quot;benefits&quot; aria-selected=&quot;true&quot;&gt;\"/>";
        //"<a class="nav-link active" id="benefits-tab" data-bs-toggle="tab" href="#benefits" role="tab"
        //  aria-controls="benefits" aria-selected="true"> "
        final String LINK_CLOSE_TAG="<x id=\"CLOSE_LINK\" ctype=\"x-a\" equiv-text=\"&lt;/a&gt;\"/>";
        //                                                                             </a>
        final String ITALIC_START_TAG="<x id=\"START_ITALIC_TEXT\" ctype=\"x-i\" equiv-text=\"&lt;i class=&quot;fas fa-arrow-down&quot;&gt;\"/>";
        //                                                                                      <i class="fas fa-arrow-down" />
        final String ITALIC_END_TAG="<x id=\"CLOSE_ITALIC_TEXT\" ctype=\"x-i\" equiv-text=\"&lt;/i&gt;\"/>";
        //                                                                                       </i>
        final String REPLACEMENT_OPENING_STRONG="<strong>";
        final String REPLACEMENT_CLOSING_STRONG="</strong>";
        int textPointer=0;
        boolean textProcessed=false;
        boolean processingBold=true;
        //only way to do this is to target the tags types
        //BOLD
        while(processingBold){
            //1) find <x tags position
            int startOfXBoldOpeningTag=equivText.indexOf(BOLD_START_TAG,textPointer);
            if(startOfXBoldOpeningTag!=-1){
                processingBold=true;
                int endOfXBoldOpeningTag=equivText.indexOf(">",startOfXBoldOpeningTag)+1;
                String openingTagcontentToReplace=equivText.substring(startOfXBoldOpeningTag,endOfXBoldOpeningTag);
                //System.out.println("x start tag for bold is = :" + openingTagcontentToReplace);//add one for the slash and then gt
                //replace at this point between the two positions above with a <b>
                equivText.replace(startOfXBoldOpeningTag,endOfXBoldOpeningTag,"<strong>");
                //calc new starting pos
                int newEndOfXBoldOpeningTag=endOfXBoldOpeningTag-openingTagcontentToReplace.length()+"<strong>".length();
                //2) find the closing tag for x tag opening with >
                int startOfXBoldClosingTag=equivText.indexOf(BOLD_END_TAG,newEndOfXBoldOpeningTag);
                int endOfXBoldClosingTag=equivText.indexOf("/>",startOfXBoldClosingTag)+2;
                String closingTagcontentToReplace=equivText.substring(startOfXBoldClosingTag,endOfXBoldClosingTag);
                //System.out.println("x end tag for bold is = :" + closingTagcontentToReplace);
                //replace at this point between the two positions above with a </b>
                equivText.replace(startOfXBoldClosingTag,endOfXBoldClosingTag,REPLACEMENT_CLOSING_STRONG);
                //calculate the length of the text that is now remaining after all that.
                //endOfXBoldClosingTag would have been textPointer however
                //we need to get the new length
                //which is end of the closing bold tag minus the length of the content we replaced plus the length of the
                //content we added.
                textPointer=endOfXBoldClosingTag-openingTagcontentToReplace.length()-closingTagcontentToReplace.length()+
                        REPLACEMENT_OPENING_STRONG.length()+REPLACEMENT_CLOSING_STRONG.length();
                //3) find closing x tag after the opening position
            }else{
                processingBold=false;
            }
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return equivText.toString();
    }
    private static String processItalicXTags(String content){
        final String METHOD_NAME="processItalicXTags";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        StringBuilder equivText=new StringBuilder();
        equivText.append(content);
        //                                                                             </a>
        final String ITALIC_START_TAG="<x id=\"START_ITALIC_TEXT\" ctype=\"x-i\" equiv-text=\"&lt;i class=&quot;fas fa-arrow-down&quot;&gt;\"/>";
        //                                                                                      <i class="fas fa-arrow-down" />
        final String ITALIC_END_TAG="<x id=\"CLOSE_ITALIC_TEXT\" ctype=\"x-i\" equiv-text=\"&lt;/i&gt;\"/>";
        int textPointer=0;
        boolean textProcessed=false;
        boolean processingItalic=true;
        //only way to do this is to target the tags types
        //italic
        while(processingItalic){
            //1) find <x tags position
            int startOfXItalicOpeningTag=equivText.indexOf("<x id=\"START_ITALIC_TEXT\"",textPointer);
            if(startOfXItalicOpeningTag!=-1){
                processingItalic=true;
                int endOfXItalicOpeningTag=equivText.indexOf(">",startOfXItalicOpeningTag)+1;
                String openingTagcontentToReplace=equivText.substring(startOfXItalicOpeningTag,endOfXItalicOpeningTag);
                //System.out.println("x start tag for bold is = :" + openingTagcontentToReplace);//add one for the slash and then gt
                //replace at this point between the two positions above with equivText
                String extractedText=extractEquivText(openingTagcontentToReplace);
                equivText.replace(startOfXItalicOpeningTag,endOfXItalicOpeningTag,extractedText);
                //again update the location as it is looking too far ahead as the content that we replaced is very long
                int updateEndOfXItalicOpeningTag=endOfXItalicOpeningTag-openingTagcontentToReplace.length()+extractedText.length();
                //2) find the closing tag for x tag opening with >
                int startOfXItalicClosingTag=equivText.indexOf("<x id=\"CLOSE_ITALIC_TEXT\"",updateEndOfXItalicOpeningTag);
                int endOfXItalicClosingTag=equivText.indexOf("/>",startOfXItalicClosingTag)+2;
                String closingTagcontentToReplace=equivText.substring(startOfXItalicClosingTag,endOfXItalicClosingTag);
                //System.out.println("x end tag for bold is = :" + closingTagcontentToReplace);
                //replace at this point between the two positions above with a </b>
                equivText.replace(startOfXItalicClosingTag,endOfXItalicClosingTag,"</i>");
                //calculate the length of the text that is now remaining after all that.
                //endOfXBoldClosingTag would have been textPointer however
                //we need to get the new length
                //which is end of the closing bold tag minus the length of the content we replaced plus the length of the
                //content we added.
                textPointer=endOfXItalicClosingTag-openingTagcontentToReplace.length()-closingTagcontentToReplace.length()+
                        extractedText.length()+"</i>".length();
                //3) find closing x tag after the opening position
            }else{
                processingItalic=false;
            }
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return equivText.toString();
    }
    private static String processLinkXTags(String content){
        final String METHOD_NAME="processLinkXTags";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        StringBuilder equivText=new StringBuilder();
        equivText.append(content);
        //                                                                             </a>
        final String LINK_START_TAG="<x id=\"START_LINK";
        final String LINK_CLOSE_TAG="<x id=\"CLOSE_LINK";
        int textPointer=0;
        boolean textProcessed=false;
        boolean processingLink=true;
        //only way to do this is to target the tags types
        //italic
        while(processingLink){
            //1) find <x tags position
            int startOfXLinkOpeningTag=equivText.indexOf(LINK_START_TAG,textPointer);
            if(startOfXLinkOpeningTag!=-1){
                processingLink=true;
                int endOfXLinkOpeningTag=equivText.indexOf(">",startOfXLinkOpeningTag)+1;
                String openingTagcontentToReplace=equivText.substring(startOfXLinkOpeningTag,endOfXLinkOpeningTag);
                //System.out.println("x start tag for link is = :" + openingTagcontentToReplace);//add one for the slash and then gt
                //replace at this point between the two positions above with equivText
                String extractedText=extractEquivText(openingTagcontentToReplace);
                equivText.replace(startOfXLinkOpeningTag,endOfXLinkOpeningTag,extractedText);
                //again update the location as it is looking too far ahead as the content that we replaced is very long
                int updateEndOfXLinkOpeningTag=endOfXLinkOpeningTag-openingTagcontentToReplace.length()+extractedText.length();
                //2) find the closing tag for x tag opening with >
                int startOfXLinkClosingTag=equivText.indexOf(LINK_CLOSE_TAG,updateEndOfXLinkOpeningTag);
                int endOfXLinkClosingTag=equivText.indexOf("/>",startOfXLinkClosingTag)+2;
                String closingTagcontentToReplace=equivText.substring(startOfXLinkClosingTag,endOfXLinkClosingTag);
                //System.out.println("x end tag for link is = :" + closingTagcontentToReplace);
                //replace at this point between the two positions above with a </b>
                equivText.replace(startOfXLinkClosingTag,endOfXLinkClosingTag,"</a>");
                //calculate the length of the text that is now remaining after all that.
                //endOfXBoldClosingTag would have been textPointer however
                //we need to get the new length
                //which is end of the closing bold tag minus the length of the content we replaced plus the length of the
                //content we added.
                textPointer=endOfXLinkClosingTag-openingTagcontentToReplace.length()-closingTagcontentToReplace.length()+
                        extractedText.length()+"</a>".length();
                //3) find closing x tag after the opening position
            }else{
                processingLink=false;
            }
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return equivText.toString();
    }
    private static String extractEquivText(String textToExtractFrom){
        final String METHOD_NAME="extractEquivText";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        final String EQUIV_TEXT_ATTR="equiv-text=\"";
        int equivTextLocation=textToExtractFrom.indexOf(EQUIV_TEXT_ATTR)+EQUIV_TEXT_ATTR.length();
        if(equivTextLocation!=-1) {
            int equivTextEndLocation = textToExtractFrom.indexOf("\"", equivTextLocation);
            String extractedText = textToExtractFrom.substring(equivTextLocation, equivTextEndLocation);
            //System.out.println("extracted text is=" + extractedText);
            LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
            return StringEscapeUtils.unescapeHtml4(extractedText);
        }else{
            LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
            return "";
        }
    }
    public static String translate(String fileContents,HashMap<String,String> translations){
        final String METHOD_NAME="translate";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        //need to have a pointer which shows us where in the search we are. This will need to be adjusted as we
        //go through to cater for the replacement of text for other sized text.  We can do this by getting the lengths
        //of string to replace and tring which is replacing
        final String I18N_ATTR="i18n=\"@@";
        int textPointer=0;
        boolean allI18NContentTranslated=false;
        StringBuilder outputContent=new StringBuilder();
        String currentTag=null;
        //LOOP THROUGH THE FILE CONTENTS
        //SEARCH FOR THE I18N ATTRIBUTE, DEFINED THE TAG CONTENTS, TRANSLATE, REPLACE AND REMOVE THE I8N TAGS.
        while(!allI18NContentTranslated){
            /*
            1) search for i18n position then work around that attribute*/
            int i18nAttrPosition=fileContents.indexOf(I18N_ATTR,textPointer);
            //System.out.println(("18Attr found at " + i18nAttrPosition));
            //if 18nAttrPosition is -1 we set allI18NContentTranslated
            if(i18nAttrPosition!=-1) {
                /* 2) find the opening less than character < */
                int openingTagPosition=fileContents.lastIndexOf("<",i18nAttrPosition);
/*            3) from that get the name of the tag that is containing this e.g. <a */
                Pattern tagNamePattern = Pattern.compile("<\\s*(\\w+)[^>]*?i18n=");
                Matcher matcher = tagNamePattern.matcher(fileContents);
                matcher.region(textPointer,fileContents.length());
                if (matcher.find()) {
                    currentTag = matcher.group(1);
                //    System.out.println("Current tag is " + currentTag);
                }
  /*        4) then get the start and end of the text we need to translate <a>this is the text</a> (maybe lengths here)*/
                int closeOfOpeningTag=fileContents.indexOf(">",openingTagPosition);
                int closingTagStart=fileContents.indexOf("</" + currentTag + ">",closeOfOpeningTag);
                //add 1 to the close of the opening tag as that is the actual closing gt sign
                String textToReplace=fileContents.substring(closeOfOpeningTag+1,closingTagStart);
                //System.out.println("Text that will be replaced is " + textToReplace);
/*          5) switch that text out "this is the text" "c'est la texte" */
                int endSpeechMarkOfI18n=fileContents.indexOf("\"",i18nAttrPosition+8);
                String keyForI18nContent=fileContents.substring(i18nAttrPosition+8,
                        endSpeechMarkOfI18n);
                //System.out.println("i18n key is " + keyForI18nContent);
                //construct the new content
                StringBuilder replacementI18nContent=new StringBuilder();
                String startTag=fileContents.substring(openingTagPosition,closeOfOpeningTag+1);
                //System.out.println("Start i18n Tag = " + startTag);
                //get translation text
                String translatedText=translations.get(keyForI18nContent);
                if(translatedText!=null&&translatedText.contains("<x id=\"START_LINK\"")){
                    translatedText=mergeAnchorTag(translatedText,textToReplace);
                }else if (translatedText!=null){
                    //nothing to do
                }else {
                    System.err.println("Aucune traduction fournie pour l’élément clé = " + keyForI18nContent);
                    translatedText=textToReplace;
                }
                //add on the closing tag for this tag.
                String closingTag= "</" + currentTag + ">";
                replacementI18nContent.append(startTag+translatedText+closingTag);
   /*         6) do the swap out of the equivText <x tag> stuff swap out</x>

            7) We need to get the length of the swapout to update the position of textPointer.*/

            /*8) remove the i18n="@@ stuff (this should be quite similar in terms of text start

            9) set the pointer to the end of the content*/
                //need to add 2 to make up for the / and the > Changed to 3 but not sure why
                int closingTagEnd=closingTagStart+currentTag.length()+3;
                //System.out.println("closing tag start = " + closingTagStart +
                 //       " closing tag end = " + closingTagEnd);
                //now set the textPointer to the end of the area we were working
                /* Copy all the text after textPointer but up until this tag content
                * that we have been working on */
                String leftContent=fileContents.substring(textPointer,openingTagPosition);
                //System.out.println("Left content to copy in is: " + leftContent);
                String contentToReplace=fileContents.substring(openingTagPosition,closingTagEnd);
                //System.out.println("New replacement content is " + replacementI18nContent.toString());
                // *!*!*!* here we would add the replaced content
                outputContent.append(leftContent);
                outputContent.append(replacementI18nContent);
                //move the text pointer forward in the file read
                textPointer=closingTagEnd;
            }else{
                //need to set the rest of the content
                outputContent.append(fileContents.substring(textPointer,fileContents.length()));
                //set the flag to true to get out of the loop as we have processed all the content
                allI18NContentTranslated=true;
            }
        }
        //System.out.println("final content for translated page = " +  outputContent.toString());
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return outputContent.toString();
    }
    public static String mergeAnchorTag(String translated,String original){
        final String METHOD_NAME="mergeAnchorTag";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        final String START_LINK="<x id=\"START_LINK";
        final String CLOSE_LINK="<x id=\"CLOSE_LINK";
        final String ANCHOR_START="<a ";
        final String ANCHOR_END="</a>";

        StringBuilder mergedContent=new StringBuilder();
        int textPointerTranslated=0;
        int textPointerOriginal=0;
        boolean processLinks=true;

        while(processLinks) {
            int startPosLinkOpenPlaceholderTranslated = translated.indexOf(START_LINK, textPointerTranslated);
            if (startPosLinkOpenPlaceholderTranslated == -1) {
                processLinks=false;
                String remainingContentToTheRight = translated.substring(textPointerTranslated, translated.length());
               // System.out.println("remaining content to the right " + remainingContentToTheRight);
                mergedContent.append(remainingContentToTheRight);
                //System.out.println("merged content = " + mergedContent.toString());
            }else {
                int endPosLinkOpenPlaceholderTranslated = translated.indexOf("/>", startPosLinkOpenPlaceholderTranslated) + 2; //for the closing ;
                String textToReplaceStartTag = translated.substring(startPosLinkOpenPlaceholderTranslated,
                        endPosLinkOpenPlaceholderTranslated);
                //System.out.println("text to replace in translated is = " + textToReplaceStartTag);
                mergedContent.append(translated.substring(textPointerTranslated, startPosLinkOpenPlaceholderTranslated));

                int startPosOpeningAnchorOriginal = original.indexOf(ANCHOR_START,textPointerOriginal);
                int endPosOpeningAnchorOriginal = original.indexOf(">", startPosOpeningAnchorOriginal) + 1;
                String textToCopyIntoTranslated = original.substring(startPosOpeningAnchorOriginal, endPosOpeningAnchorOriginal);
                //System.out.println("text to copy into translated = " + textToCopyIntoTranslated);
                mergedContent.append(textToCopyIntoTranslated);

                int closeTagPlaceholderTranslated = translated.indexOf(CLOSE_LINK,endPosLinkOpenPlaceholderTranslated);
                //int closeTagPlaceholderClosingTagTranslated=closeTagPlaceholderTranslated+CLOSE_LINK.length()+2; doesnt work as there are repeats of other attrs
                int closeTagPlaceholderClosingTagTranslated = translated.indexOf("/>", closeTagPlaceholderTranslated) + 2; //for the slash and the gt
                //add the translated text between the tags
                mergedContent.append(translated.substring(endPosLinkOpenPlaceholderTranslated, closeTagPlaceholderTranslated));
                //now we replace the strange close link with a normal close link
                String textToReplaceCloseTag = translated.substring(closeTagPlaceholderTranslated, closeTagPlaceholderClosingTagTranslated);
                //System.out.println("Text in close tag translated to replace is " + textToReplaceCloseTag);
                mergedContent.append(ANCHOR_END);
                //
                textPointerTranslated=closeTagPlaceholderClosingTagTranslated;
                textPointerOriginal=endPosOpeningAnchorOriginal;
            }
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return mergedContent.toString();
    }
    public static String translate(List<String> lines, HashMap<String, String> translations){
        final String METHOD_NAME="translate";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        //start of the method
        //variable to hold the output
        List<String> outputLines = new ArrayList<>();
        List<String> tagBuffer = new ArrayList<>();
        boolean insideTagBlock = false;
        boolean collectingOpeningTag = false;
        boolean previousLinesCollected=false;
        String currentTag = null;
        StringBuilder openingTagBuilder = new StringBuilder();

        //best to change this to a counter as I need to be able to go back in the index and
        //rewrite the output
        for (int iLinePointer=0;iLinePointer<lines.size();iLinePointer++) {
            String line=lines.get(iLinePointer);
            //inside tag block means we are processing an i18n tag
            if (!insideTagBlock) {
                //if the line contains the i18n tag or we are following on from
                //detecting this
                if (line.contains("i18n=\"@@") || collectingOpeningTag) {
                    //Find the index of the identifier
                    int indexI18=line.indexOf("i18n=\"@@");
                    //need to fix this so that it can work if multiple i18n on same line which could happen
                    if(indexI18>0&&!previousLinesCollected) {
                        //check if there is an opening tag for this i18n (it is the closest one to the i18n
                        //in the direction from right to left (if on the same line)
                        int tagNameStartIdx = line.lastIndexOf("<", indexI18);
                        int openingTagLinesBack = 0;
                        //this means that the opening tag is not on this line and we need to go back
                        if (tagNameStartIdx == -1) {
                            //get the start number back we need to start on to copy the text over
                            //this is the number of lines we have to go back to.
                            openingTagLinesBack = traversePreviousLinesForStartTag(outputLines);
                            //System.out.println("identified row with " + openingTagLinesBack + " lines to go back to");
                            // start at the number of rows - the number of rows back from openingTagLinesBack
                            int startIndexForInputLinesProcessing = iLinePointer - openingTagLinesBack;
                            //                        so this line includes the opening tag but it could be halfway through
                            //split the row before we loop through the rest as it needs different handling
                            String lineContent = lines.get(startIndexForInputLinesProcessing);
                            int openingTagIdx = lineContent.lastIndexOf("<");
                            String leftSideContent = lineContent.substring(0, openingTagIdx);
                            //System.out.println("left side content is " + leftSideContent);
                            String rightsideContent = lineContent.substring(openingTagIdx);
                            //System.out.println(("Right side content i  " + rightsideContent));
                            openingTagBuilder.append(rightsideContent);
                            //can't remove the lines as that will mess up the indexing
                            //instead set the content to whatever was left on the split
                            //need to calculate pointer to output lines which should be populated at least with the
                            //preceding lines
                            int outputLineLenth = outputLines.size();
                            //System.out.println("there are " + outputLineLenth + " in the output at the moment");
                            int currentPositionInOutput = outputLineLenth - openingTagLinesBack;
                            //use the calculated position back
                            outputLines.set(currentPositionInOutput, leftSideContent);
                            //check if the number back was greater than 1 (that means we need to do something)
                            if (openingTagLinesBack > 1) {
                                //loop through the lines and add them to the outputTagBuilder
                                //set the equivalent file lines to blank
                                int outputLinesCurrentSize=outputLines.size();
                                //we have already split the content where the tag opens so we have
                                //to calculate the start index as size of output back as many rows as is needed
                                //then add 1 to skip the one we already processed
                                for (int i = outputLinesCurrentSize-openingTagLinesBack +1; i < outputLinesCurrentSize; i++) {
                                    //blank the line as we are putting it in the tag output instead
                                    outputLines.set(i, "");
                                    //System.out.println("Removing line " + i);
                                    //put the content into the openingTagBuilder so that it has the start of the tag and any contetnt
                                    //up to the current i18n attribute
                                    //.out.println("writing the following to buffer for tag " + lines.get(i));
                                }
                                for(int i=lines.size()-openingTagLinesBack;i<lines.size()-1;i++){
                                    openingTagBuilder.append(lines.get(i));
                                }
                            }
                            previousLinesCollected = true;
                            //tag and content if any to the left of it.
                        }
                    }
                    // Begin collecting the full opening tag
                    collectingOpeningTag = true;
                    openingTagBuilder.append(line).append("\n");
                   // System.out.println("opening Tag Builder content is " + openingTagBuilder.toString());
                    tagBuffer.add(line);

                    if (line.contains(">")) {
                        // Full tag opening completed
                        String openingTag = openingTagBuilder.toString();
                        Pattern tagNamePattern = Pattern.compile("<\\s*(\\w+)[^>]*?i18n=");
                        Matcher matcher = tagNamePattern.matcher(openingTag);

                        if (matcher.find()) {
                            currentTag = matcher.group(1);
                            insideTagBlock = true;
                            collectingOpeningTag = false;
                            previousLinesCollected=false;
                            openingTagBuilder.setLength(0); // Reset

                            // Handle if the closing tag is already here
                            if (openingTag.contains("</" + currentTag + ">")) {
                                String processed = processTagBlock(tagBuffer, translations,
                                        currentTag);
                              //  System.out.println("procesed block = " + processed);
                                outputLines.addAll(List.of(processed.split("\n")));
                                insideTagBlock = false;
                                currentTag = null;
                                tagBuffer.clear();
                            }
                        } else {
                            // Malformed? Keep going next line just in case.
                            collectingOpeningTag = false;
                            openingTagBuilder.setLength(0);
                            outputLines.addAll(tagBuffer);
                            tagBuffer.clear();
                        }
                    }
                } else {
                    outputLines.add(line); // Line has nothing to do with i18n
                }
            } else {
                tagBuffer.add(line);

                if (line.contains("</" + currentTag + ">")) {
                    String processed = processTagBlock(tagBuffer, translations,currentTag);
                    outputLines.addAll(List.of(processed.split("\n")));
                    insideTagBlock = false;
                    currentTag = null;
                    tagBuffer.clear();
                }
            }
        }
        // Handle leftover if file ends unexpectedly inside tag block
        if (!tagBuffer.isEmpty()) {
            String processed = processTagBlock(tagBuffer, translations,currentTag);
            outputLines.addAll(List.of(processed.split("\n")));
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return String.join(System.lineSeparator(), outputLines);
    }

    /**
     * @return back the number of lines back the opening tag is in
     */
    public static int traversePreviousLinesForStartTag(List<String> outputLines){
        final String METHOD_NAME="traversePreviousLinesForStartTag";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        //loop through the lines from newest to oldest
        //need to add 1 to this I think @todo
        int sizeOfList=outputLines.size()-1;
        int rowCounter=1;
        for (int i=sizeOfList;i>0;i--){
            if(outputLines.get(i).contains("<")){
                //System.out.println("opening tag found on row " + i);
                return rowCounter;
            }
            rowCounter++;
        }
        System.err.println("Error identifying start tag");
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        //search for the nearest < tag.  This is the opening of the tag that contains i18n
        return -1;
    }
    /**
     * Call this for each file.  It will transform the underlying file copy and then overwrite it
     * with the newly translated version.
     * @param inputFilePath
     * @param translation
     * @throws IOException
     */
    public static void processFile(Path inputFilePath, HashMap<String, String> translation) throws IOException {
        final String METHOD_NAME="processFile";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        String originalContent;
        String fileContent = Files.readString(inputFilePath);
        try {
            //first translate content
            String translatedContent = translate(fileContent, translation);
            //now replace the angular tags in that replaced content with the underlying
            //html text from the equiv texts
            translatedContent = replaceXEquivText(translatedContent);
            //translatedContent=replaceAsAt(translatedContent);
            translatedContent = removeI18nMarkup(translatedContent);
            //remove the annoying @ in the html
            translatedContent = replaceAsAt(translatedContent);
            try (BufferedWriter writer = Files.newBufferedWriter(inputFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(translatedContent);
            }
        }catch(Exception ioe){
            System.out.println("Erreur avec le chemin inputFilePath " + inputFilePath);
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
    public static String removeI18nMarkup(String fileContents){
        final String METHOD_NAME="removeI18nMarkup";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        final String I18N_ATTR="i18n=\"@@";
        int textPointer=0;
        boolean allI18NContentRemoved=false;
        StringBuilder outputContent=new StringBuilder();
        //LOOP THROUGH THE FILE CONTENTS
        //SEARCH FOR THE I18N ATTRIBUTE, DEFINED THE TAG CONTENTS, TRANSLATE, REPLACE AND REMOVE THE I8N TAGS.
        while(!allI18NContentRemoved){
            /*
            1) search for i18n position then work around that attribute*/
            int i18nAttrPosition=fileContents.indexOf(I18N_ATTR,textPointer)-1; //remove 1 as it's def got a space infront of the i18n
            //if 18nAttrPosition is -1 we set allI18NContentTranslated
            if(i18nAttrPosition!=-2) { //this is because of the minus one to go back on the space
                int endSpeechMarkOfI18n=fileContents.indexOf("\"",i18nAttrPosition+I18N_ATTR.length()+1);
                String contentToAdd=fileContents.substring(textPointer,i18nAttrPosition);
                //System.out.println("content To Add is " + contentToAdd);
                //attach the content up until i18n  then we will set the next pointer after it.
                outputContent.append(contentToAdd);
                //move the text pointer forward in the file read
                textPointer=endSpeechMarkOfI18n+1;
            }else{
                //set the flag to true to get out of the loop as we have processed all the content
                allI18NContentRemoved=true;
                outputContent.append(fileContents.substring(textPointer));
            }
        }
        //System.out.println("final content for translated page = " +  outputContent.toString());
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return outputContent.toString();
    }
    public static String replaceAsAt(String content){
        final String METHOD_NAME="replaceAsAt";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        //text or numbers or space either side of an ampersand as this is not allowed in angular
        String regExp="(?<=[\\w\\s])@(?=[\\w\\s])";
        String result = content.replaceAll(regExp, "&#64;");
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return result;
    }
}
