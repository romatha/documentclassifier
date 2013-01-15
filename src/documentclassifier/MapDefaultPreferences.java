package documentclassifier;

import java.util.HashMap;

/**
 * The purpose of this class is to mantain, in a structured and ordered way, two kinds of information
 * for other classes of the program:
 * 1 - The names of the various preferences
 * 2 - Their corresponding default values
 * 
 * In this way we add a level of abstraction for the other classes of the program, that need to
 * use the static fields of this class to reference the names and/or default values of the various
 * preferences.
 * Analogously, to modify the default values of the various preferences, the programmer simply needs
 * modify a row of this class' constructor.
 * 
 * @author      Salvo Danilo Giuffrida (salvod.giuffrida@gmail.com)
 */
public class MapDefaultPreferences extends HashMap<String, String> {

    private static final DocumentClassifierApp application = DocumentClassifierApp.getApplication();
    /**
     * This class must not be serialized, therefore the value of the field 'serialVersionUID'
     * is not important.
     */
    private static final long serialVersionUID = 0;
    private static final char pathSeparator = '/';
    /**
     * It follows the names of the various preferences.
     */
    /**
     * This preference contains the name of the current scraper, used to extract the features (that is, the single terms)
     * from the query document, and from documents of the training set.
     */
    public static final String SCRAPER = "Scraper";
    /**
     * If removal of stopwords from each document is enabled or not.
     */
    public static final String isREMOVALSTOPWORDS = "isStopWords";
    /**
     * The path of the file containing the list of stopwords to remove from each document.
     */
    public static final String STOPWORDSLIST = "StopWords";
    /**
     * If stemming of terms from each document is enabled or not.
     */
    public static final String isSTEMMING = "isStemming";
    /**
     * The name of the class which implements the current stemmer, which is applied
     * to each document during the pre-processing phase.
     */
    public static final String STEMMER = "Stemmer";
    /**
     * The name of the current metric, used to calculate the distance between the query
     * document and each document of the training set.
     */
    public static final String METRIC = "Metric";
    /**
     * The value of K for the K-NN, used during the classification phase to determine
     * the most frequent category between the K documents closest to the query document,
     * that is then assigned to it.
     */
    public static final String KNN = "KNN";
    /**
     * The maximum value of K for the K-NN during the validation phase, chosen
     * by the user through the window of the preferences: The K-Fold cross validation
     * is repeated with different values of K-NN, from 1 to this value.
     */
    public static final String MAXKNNVALIDATION = "MaxKNN";
    /**
     * The value of K for the K-Fold cross validation, that is, the number of partitions
     * the training set is divided into, and that are in turn taken into consideration
     * as the current control set.
     */
    public static final String KFOLD = "KFOLD";
    /**
     * If the training set must be partitioned using a stratified sampling or not.
     * With stratified sampling, each partition will contain the same number of documents
     * from each category; in this way, even if the value of K-Fold is mantained constant,
     * it should be possible to reduce the classification error, because the training set
     * each control set is compared to is perfectly balanced (it contains the same number
     * of samples for each category).
     */
    public static final String isSTRATIFIED = "isStratified";
    /**
     * If logging during the validation phase is enabled or not.
     */
    public static final String isLOGGING = "isLogging";
    /**
     * The path of the log file.
     */
    public static final String LOGFILE = "LogFile";
    /**
     * If the log file must be overwritten each time it is openened or not.
     */
    public static final String isOVERWRITELOGFILE = "isOverwriteLogFile";
    /**
     * If, during the validation phase, the URL, title and text of the current
     * document must be visualized in the graphical interface.
     */
    public static final String isVISUALIZECURRENTDOCUMENT = "isVisualizeCurrentDocument";
    /**
     * If, during the validation phase, the list of documents of the training set,
     * ranked to the current query, must be visualized or not.
     */
    public static final String isVISUALIZELISTRANKEDDOCUMENTS = "isVisualizeListRankedDocuments";
    /**
     * The path of the training set's directory.
     */
    public static final String TRAININGSETDIRECTORY = "Training set directory";

    /**
     * This constructor creates a new map of names and default values for the program's preferences.
     */
    public MapDefaultPreferences() {

        super();
        /**
         * First, the path of the directory the program is executed from is determined.
         */
        String programParentDir = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        programParentDir = programParentDir.substring(0, programParentDir.lastIndexOf(pathSeparator) + 1);
        /**
         * The default assumption is that the training set is in a subdirectory 'TrainingSet'
         * of this one.
         */
        put(TRAININGSETDIRECTORY, programParentDir + "TrainingSet");
        /**
         * The default scraper is the one of the ANSA website ({@link ANSAScraper}),
         * the only one available for now, inside the packge {@link documentclassifier.Scrapers}.
         */
        put(SCRAPER, application.getScrapersPackageName() + ".ANSAScraper");
        /**
         * The default stopwords list is the one for the Italian language.
         */
        put(STOPWORDSLIST, programParentDir + "StopWords" + pathSeparator + "Italian.txt");
        /**
         * The removal of stopwords from each document is by default enabled.
         */
        put(isREMOVALSTOPWORDS, "true");
        /**
         * The stemming of terms from each document is by default enabled.
         */
        put(isSTEMMING, "true");
        /**
         * The default stemmer is the one for the Italian language, inside the package
         * 'org.tartarus.snowball.ext'.
         */
        put(STEMMER, application.getStemmersPackageName() + ".italianStemmer");
        /**
         * The default metric, to calculate the distance between documents, is the 'TF-IDF'.
         */
        put(METRIC, "TF-IDF");
        /**
         * The default value of K for K-NN is 1.
         */
        put(KNN, "1");
        /**
         * The maximum default value of K-NN during the validation phase is 1.
         */
        put(MAXKNNVALIDATION, get(KNN));
        /**
         * The value of K for K-Fold cross-validation is by default 10, since this
         * is the most used value, representing a good compromise between accuracy
         * and speed.
         */
        put(KFOLD, "10");
        /**
         * The stratified sampling for K-Fold cross validation is by default disabled.
         */
        put(isSTRATIFIED, "false");
        /**
         * The logging of status messages during the validation phase is by default disabled
         * (for performance reasons).
         */
        put(isLOGGING, "false");
        /**
         * The default path of the log file (taken into consideration if logging is enabled)
         * is './LogFile.log'.
         */
        put(LOGFILE, programParentDir + "LogFile.log");
        /**
         * By default the log file is overwritten each time it is opened.
         */
        put(isOVERWRITELOGFILE, "false");
        /**
         * The visualization of information of the current document (URL, title, text),
         * during the validation phase, is by default disabled (for performance reasons).
         */
        put(isVISUALIZECURRENTDOCUMENT, "false");
        /**
         * The visualization of documents of the training set, ranked to the current
         * query, during the validation phase, is by default disabled (for performance
         * reasons).
         */
        put(isVISUALIZELISTRANKEDDOCUMENTS, "false");
    }
}
