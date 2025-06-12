package com.mcogan;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.toString());
    final static String XLFFILEPATH="xlfFilePath";
    final static String ROOTFOLDER="rootFolder";
    final static String TEMPFOLDER="tempFolder";
    final static String LOG_VERBOSE="logLevel";
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
    public static void main(String[] args) throws IOException {
        final String METHOD_NAME="main";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        LOG.info(messages.getString(MessageKeys.MESSAGE_LOCALE) + " = " + usingLocale);
        LOG.info(messages.getString(MessageKeys.DEMARRAGE_APP));
        LOG.info(messages.getString(MessageKeys.VERIFICATION_DES_ARGUMENTS));
        //map arguments so we can access them through the map and keys
        HashMap<String, String> argMap = parseArgsToMap(args);
        //check if we have verbose on
        setLoggingLevel(argMap.get(LOG_VERBOSE));
        //parse the xlf key value files
        HashMap<String, String> translations = parseXlfToMap(argMap.get(XLFFILEPATH));
        //make a temp directory so that we don't hose the actual files
        Path tempDir = Paths.get(argMap.get(TEMPFOLDER));
        copyHtmlFilesToTemp(Paths.get(argMap.get(ROOTFOLDER)),tempDir);
        //now we use the 18n replacer
        translateFilesInTemp(tempDir,translations);
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
    private static void setLoggingLevel(String logLevel){
        final String METHOD_NAME="setLoggingLevel";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        if(logLevel!=null&&logLevel.equalsIgnoreCase("v")) {
            LOG.info("Le niveau de journalisation est verbeux.");
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            rootLogger.setLevel(Level.FINE);
        }else{
            LOG.info("Changement du niveau de journalisation : la trace des méthodes ne sera plus disponible.");
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            rootLogger.setLevel(Level.WARNING);
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
    public static void translateFilesInTemp(Path tempDir,HashMap<String,String> translations) throws IOException{
        final String METHOD_NAME="translateFilesInTemp";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".html")) {
                    MultilineI18nReplacer.processFile(file,translations);
                }
                LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
                return FileVisitResult.CONTINUE;
            }
        });
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
    public static HashMap<String, String> parseArgsToMap(String[] args) throws RuntimeException{
        final String METHOD_NAME="parseArgsToMap";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        HashMap<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.contains("=")) {
                String[] parts = arg.split("=", 2);
                String key = parts[0].trim();
                LOG.info(METHOD_NAME + messages.getString(MessageKeys.CLE) + key);
                String value = parts[1].trim();
                LOG.info(METHOD_NAME+ messages.getString(MessageKeys.VALEUR_DE_LARG) + value);
                map.put(key, value);
            } else {
                LOG.log(Level.SEVERE,messages.getString(MessageKeys.IGNORER_ARG_INCONNU) + arg);
            }
        }
        StringBuilder argMissingMessage=new StringBuilder();
        //check the required params are there
        if(map.get(XLFFILEPATH)==null){
            argMissingMessage.append(messages.getString(MessageKeys.CHEMIN_DE_FICHIER_XLF_MANQUANT));
        }
        if(map.get(ROOTFOLDER)==null){
            argMissingMessage.append(messages.getString(MessageKeys.LE_DOSSIER_RACINE_EST_MANQUANT));
        }
        if(map.get(TEMPFOLDER)==null){
            LOG.info(messages.getString(MessageKeys.LE_DOSSIER_TEMP));
        }
        if(map.get(LOG_VERBOSE)==null){
            LOG.info("Le niveau de journalisation est verbeux.");
        }
        if(argMissingMessage.length()>0){
            throw new RuntimeException(argMissingMessage.toString());
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return map;
    }
    public static HashMap<String, String> parseXlfToMap(String filePath) throws IOException {
        final String METHOD_NAME="parseXlfToMap";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        String content= Files.readString(Path.of(filePath));
        HashMap<String,String> map=new HashMap<>();
        final String TRANS_UNIT_ID="id=\"";
        final String TARGET_TEXT="<target>";
        boolean stillProcessing=true;
        int textPointer=0;
        while(stillProcessing){
            int transUnitStart=content.indexOf("<trans-unit",textPointer);
            if(transUnitStart!=-1) {
                //find the id
                int transUnitIdPosition = content.indexOf("id=", transUnitStart);
                //find the end speechmark of the id
                int transUnitIdClose = content.indexOf("\"", transUnitIdPosition + TRANS_UNIT_ID.length());
                String transUnitId = content.substring(transUnitIdPosition + TRANS_UNIT_ID.length(), transUnitIdClose);
                //now find the translation text
                int targetTextStart = content.indexOf(TARGET_TEXT, transUnitIdClose);
                int targetTextEnd = content.indexOf("</target>", targetTextStart);
                String translatedContent=content.substring(targetTextStart+TARGET_TEXT.length(), targetTextEnd);
                //System.out.println("target = " + translatedContent);
                textPointer = targetTextEnd;
                map.put(transUnitId,translatedContent);
            }else{
                stillProcessing=false;
            }
        }
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return map;
    }
    public static Path copyHtmlFilesToTemp(Path sourceDir, Path tempDir) throws IOException {
        final String METHOD_NAME="copyHtmlFilesToTemp";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        if (Files.exists(tempDir)) {
            deleteDirectory(tempDir);  // Start clean
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = tempDir.resolve(sourceDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = tempDir.resolve(sourceDir.relativize(file));
                Files.copy(file, targetFile);
                return FileVisitResult.CONTINUE;
            }
        });
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
        return tempDir;
    }
    private static void deleteDirectory(Path path) throws IOException {
        final String METHOD_NAME="deleteDirectory";
        LOG.info(messages.getString(MessageKeys.METHOD_ENTRY_LOG)+METHOD_NAME );
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        LOG.info(messages.getString(MessageKeys.METHOD_EXIT_LOG)+ METHOD_NAME);
    }
}