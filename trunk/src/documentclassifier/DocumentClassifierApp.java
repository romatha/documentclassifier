
package documentclassifier;

import documentclassifier.Scrapers.Scraper;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;

/**
 * Main class of the application
 * <p>
 * Beside the implementation of some methods of the class {@link SingleFrameApplication},
 * which is extended to personalize its behaviour, this class contains:
 * <p>
 *  - A method ({@link #preferenceChange preferenceChange}), which implements the interface {@link PreferenceChangeListener},
 * called each time that the values of one or more preferences are modified (by methods of this or other classes of the application,
 * which register this class as their PreferenceChangeListener)
 * <p>
 *  - Methods to get the values of the various preferences
 * <p>
 *  - A method ({@link #generateTrainingSet generateTrainingSet}) to (re)generate the training set every time that it is necessary
 * <p>
 *  - A method ({@link #readDocument readDocument}) to read the title and the text of a document from the file system,
 * and from these create an instance of the class {@link Document}
 * 
 * @author      Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 * @see         SingleFrameApplication
 */
public class DocumentClassifierApp extends SingleFrameApplication implements PreferenceChangeListener {

    /**
     * This is a variabile that contains a reference to the global application's preferences node.
     * In this case, the reference is to the node of the user currently running the application,
     * which can be implemented in a different way depending by the platform where the program is being
     * executed (for example as an hidden file inside the $HOME directory in Unix systems, or as registry
     * keys for Windows systems).
     * Every method of this or of other classes that want to access (reading or writing) the values of the
     * preferences, do this using the methods {@link Preferences#get get} and {@link Preferences#put put}
     * of this variable.
     * In this way we added an abstraction level for the access to the preferences, and if we would decide
     * to change the preferences node containing the values of interest for this application, we'll just
     * have to change the following line.
     */
    private static final Preferences preferences = Preferences.userRoot();
    /**
     * Map that associates the name of every preference to its default value.
     */
    private static final MapDefaultPreferences mapDefaultPreferences = new MapDefaultPreferences();
    private ResourceBundle documentClassifierAppResources = java.util.ResourceBundle.getBundle("documentclassifier/resources/DocumentClassifierApp");
    /**
     * The current training set represented like a set of subsets of documents
     * (the subsets are made of all the documents belonging to the same category).
     */
    private Set<Set<Document>> trainingSet;
    private int trainingSetSize = 0;
    /**
     * Configuration variables, corresponding to the preferences of the application.
     */
    private Scraper Scraper;
    private String trainingSetDirectory,  logFile,  stopWordsList,  metric;
    private boolean isRemovalStopWords,  isStemming,  isStratified,  isLogging,  isOverwriteLogFile,  isVisualizeCurrentDocument,  isVisualizeDocumentsList;
    private String currentStemmer;
    private int KNN,  KFold,  maximumKNNValidation;

    /**
     * Responsible for initializations that must occur before the GUI is constructed by startup.
     * 
     * @param args          The arguments passed to the application through the command line.
     */
    @Override
    protected void initialize(String[] args) {
        super.initialize(args);
        /*
         * A PreferenceChangeEvent is manually generated, to force during the startup of the
         * program the reading of the preferences (from the node represented by the variable
         * 'preferences'), and the initialization of the corresponding internal variables of the
         * class.
         */
        preferenceChange(new PreferenceChangeEvent(preferences, "All", "All"));
    }

    /**
     * At startup create and show the main frame of the application.
     */
    @Override
    protected void startup() {
        show(new DocumentClassifierView(this));
    }

    /**
     * A convenient static getter for the application instance.
     * @return the instance of DocumentClassifierApp.
     */
    public static DocumentClassifierApp getApplication() {
        return Application.getInstance(DocumentClassifierApp.class);
    }

    /**
     * Main method launching the application.
     * @param args 
     */
    public static void main(String[] args) {
        launch(DocumentClassifierApp.class, args);
    }

    /**
     * Method that cancels all the preferences' values, restoring them to their default ones.
     */
    private void cancelPreferences() {
        try {
            preferences.clear();
        } catch (BackingStoreException ex) {
            DocumentClassifierView.showErrorMessage(ex.toString());
        }
    }

    /**
     * Method that implements the interface 'PreferenceChangeListener'.
     * <p>
     * It's invoked each time a preference of the node represented by the
     * variable 'preferences' is modified, changing the value and/or the state
     * of one or more components of the JDialog {@link PreferencesDialog}.
     * When a preference is modified, this method updates the value of the
     * corresponding variable inside this class.
     *  
     * @param event             The PreferenceChangeEvent that must be managed.
     */
    public void preferenceChange(PreferenceChangeEvent event) {
        try {
            String eventKey = event.getKey();
            String currentKey, defaultValue;

            currentKey = MapDefaultPreferences.SCRAPER;
            Scraper = getScraperInstance(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.isREMOVALSTOPWORDS;
            isRemovalStopWords = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.STOPWORDSLIST;
            defaultValue = mapDefaultPreferences.get(currentKey);
            stopWordsList = preferences.get(currentKey, defaultValue);
            if (!new File(stopWordsList).isFile()) {
                String message = "The specified stopwords file (" + stopWordsList + ") is not valid,\n";
                stopWordsList = defaultValue;
                if (new File(stopWordsList).isFile() && isRemovalStopWords) {
                    DocumentClassifierView.showErrorMessage(message + "the default value is used (" + stopWordsList + " )");
                } else if (!new File(stopWordsList).isFile() && isRemovalStopWords) {
                    DocumentClassifierView.showErrorMessage(message + "the default file (" + stopWordsList + ") is not present,\n" + "the removal of stopwords has been disabled");
                    isRemovalStopWords = false;
                    preferences.put(MapDefaultPreferences.isREMOVALSTOPWORDS, String.valueOf(isRemovalStopWords));
                }
            }

            currentKey = MapDefaultPreferences.isSTEMMING;
            isStemming = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.STEMMER;
            currentStemmer = preferences.get(currentKey, mapDefaultPreferences.get(currentKey));

            //(Re)Generation of the training set
            currentKey = MapDefaultPreferences.TRAININGSETDIRECTORY;
            trainingSetDirectory = preferences.get(currentKey, defaultValue);
            if (eventKey.equals("All") || eventKey.equals(MapDefaultPreferences.isSTEMMING) || eventKey.equals(MapDefaultPreferences.STOPWORDSLIST)) {
                defaultValue = mapDefaultPreferences.get(currentKey);
                if (!generateTrainingSet(trainingSetDirectory)) {
                    trainingSetDirectory = defaultValue;
                    String message = "The path specified for the training set directory is not valid, ";
                    if (generateTrainingSet(trainingSetDirectory)) {
                        DocumentClassifierView.showErrorMessage(message +
                                "the default value is used (" + trainingSetDirectory + ")");
                    } else {
                        DocumentClassifierView.showErrorMessage(message +
                                "and the default directory (" + trainingSetDirectory + ") is not present.\n" + "Classification not possible");
                    }
                }
            }

            currentKey = MapDefaultPreferences.METRIC;
            metric = preferences.get(currentKey, mapDefaultPreferences.get(currentKey));

            currentKey = MapDefaultPreferences.KNN;
            KNN = Integer.parseInt(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.MAXKNNVALIDATION;
            maximumKNNValidation = Integer.parseInt(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.KFOLD;
            KFold = Integer.parseInt(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.isSTRATIFIED;
            isStratified = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.isLOGGING;
            isLogging = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.LOGFILE;
            logFile = preferences.get(currentKey, mapDefaultPreferences.get(currentKey));

            currentKey = MapDefaultPreferences.isOVERWRITELOGFILE;
            isOverwriteLogFile = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.isVISUALIZECURRENTDOCUMENT;
            isVisualizeCurrentDocument = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

            currentKey = MapDefaultPreferences.isVISUALIZELISTRANKEDDOCUMENTS;
            isVisualizeDocumentsList = Boolean.parseBoolean(preferences.get(currentKey, mapDefaultPreferences.get(currentKey)));

        } catch (Exception ex) {
            DocumentClassifierView.showErrorMessage(ex.toString());
            cancelPreferences();
        }
    }

    /**
     * Method that reads from an InputStream (FileInputStream, NetworkInputStream, ecc..) the title
     * and the text of the document associated with it, using an appropriate scraper, and it returns
     * them inside a vector of strings (position 0: Title, position 1: Text).
     * 
     * @param  IS               The InputStream from where reading the document.
     * @return                  The title and the text of the extracted document, inside a String vector made of two elements.
     * @throws Exception 
     */
    protected String[] readDocument(InputStream IS) throws Exception {
        return Scraper.getDocument(IS);
    }

    /**
     * Method that returns the list of classes inside the package provided in input.
     * 
     * @param   packagename     The name of the package we want to know the classes of.
     * @return                  The list of classes (as instances of the class {@link Class})
     *                          present inside the specified package.
     * @throws java.lang.ClassNotFoundException
     */
    protected static List<Class> getClassesForPackage(String packagename) throws ClassNotFoundException {
        // This will hold a list of directories matching the pckgname. 
        // There may be more than one if a package is split over multiple jars/paths
        List<Class> classes = new ArrayList<Class>();
        ArrayList<File> directories = new ArrayList<File>();
        try {
            ClassLoader cld = Thread.currentThread().getContextClassLoader();
            if (cld == null) {
                throw new ClassNotFoundException("Can't get class loader.");
            }
            // Ask for all resources for the path
            Enumeration<URL> resources = cld.getResources(packagename.replace('.', '/'));
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                if (res.getProtocol().equalsIgnoreCase("jar")) {
                    JarURLConnection conn = (JarURLConnection) res.openConnection();
                    JarFile jar = conn.getJarFile();
                    for (JarEntry e : Collections.list(jar.entries())) {

                        if (e.getName().startsWith(packagename.replace('.', '/')) && e.getName().endsWith(".class") && !e.getName().contains("$")) {
                            String className =
                                    e.getName().replace("/", ".").substring(0, e.getName().length() - 6);
                            //System.out.println(className);
                            classes.add(Class.forName(className));
                        }
                    }
                } else {
                    directories.add(new File(URLDecoder.decode(res.getPath(), "UTF-8")));
                }
            }
        } catch (NullPointerException x) {
            throw new ClassNotFoundException(packagename + " does not appear to be a valid package (Null pointer exception)");
        } catch (UnsupportedEncodingException encex) {
            throw new ClassNotFoundException(packagename + " does not appear to be a valid package (Unsupported encoding)");
        } catch (IOException ioex) {
            throw new ClassNotFoundException("IOException was thrown when trying to get all resources for " + packagename);
        }

        // For every directory identified capture all the .class files
        for (File directory : directories) {
            if (directory.exists()) {
                // Get the list of the files contained in the package
                String[] files = directory.list();
                for (String file : files) {
                    // we are only interested in .class files
                    if (file.endsWith(".class")) {
                        // removes the .class extension
                        classes.add(Class.forName(packagename + '.' + file.substring(0, file.length() - 6)));
                    }
                }
            } else {
                throw new ClassNotFoundException(packagename + " (" + directory.getPath() +
                        ") does not appear to be a valid package");
            }
        }
        return classes;
    }

    /**
     * The following methods allow read access to the internal variables of this class that correspond
     * to one of the program's preferences. Each one of these methods takes in input a boolean value,
     * which indicates if the caller must get the default value of the corresponding variable, or the actual
     * one.
     */
    /**
     * Method that returns the path of the training set directory.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The path of the training set directory.
     */
    protected String getTrainingSetDirectory(boolean defaultValue) {
        return (defaultValue) ? mapDefaultPreferences.get(MapDefaultPreferences.TRAININGSETDIRECTORY) : trainingSetDirectory;
    }

    /**
     * Method that returns a boolean value which indicates if the removal of stopwords from documents is enabled or not.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value which indicates if the removal of stopwords from documents is enabled or not.
     */
    protected boolean isRemovalStopWords(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isREMOVALSTOPWORDS)) : isRemovalStopWords;
    }

    /**
     * Method that returns the path of the file containing the list of stopwords.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The path of the file containing the list of stopwords.
     */
    protected String getStopWordsList(boolean defaultValue) {
        return (defaultValue) ? mapDefaultPreferences.get(MapDefaultPreferences.STOPWORDSLIST) : stopWordsList;
    }

    /**
     * Method that returns the path of the log file.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The path of the log file.
     */
    protected String getLogFile(boolean defaultValue) {
        return (defaultValue) ? mapDefaultPreferences.get(MapDefaultPreferences.LOGFILE) : logFile;
    }

    /**
     * Method that returns a boolean value indicating if logging of messages during validation is enabled or not.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value indicating if logging of messages during validation is enabled or not.
     */
    protected boolean isLogging(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isLOGGING)) : isLogging;
    }

    /**
     * Method that returns the reference to the map "name"->"default value" of each preference.
     * 
     * @return                  The reference to the map "name"->"default value" of each preference.
     */
    protected MapDefaultPreferences getMapPreferences() {
        return mapDefaultPreferences;
    }

    /**
     * Method that returns the name of the metric currently used to calculate the distance between each document
     * of the training set and the query document.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The name of the metric.
     */
    protected String getMetric(boolean defaultValue) {
        return (defaultValue) ? mapDefaultPreferences.get(MapDefaultPreferences.METRIC) : metric;
    }

    /**
     * Method that returns the current value of K for K-Fold cross validation.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The current value of K for K-Fold cross validation.
     */
    protected int getKFold(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mapDefaultPreferences.get(MapDefaultPreferences.KFOLD)) : KFold;
    }

    /**
     * Method that returns the maximum value of K for K-Fold cross validation, dependant on the dimension of the
     * training set.
     * <p>
     * This value is used by the class {@link PreferencesDialog} during the drawing of its GUI, to define the 'MaxValue' property
     * of the JSlider associated to the value of K-Fold.
     * 
     * @return                  The maximum value of K for K-Fold cross validation.
     */
    protected int getKFoldMaximum() {
        return trainingSetSize;
    }

    /**
     * Method that returns the minimum value of K for K-Fold cross validation.
     * <p>
     * Like the previous method, this one is also used by the class {@link PreferencesDialog} during the initialization
     * of its GUI, to define the property 'MinValue' of the JSlider associated with the value of K-Fold.
     * 
     * @return                  The minimum value of K for K-Fold cross validation.
     */
    protected int getKFoldMinimum() {
        return 2;
    }

    /**
     * Method that returns the current value of K for K-NN.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The current value of K for K-NN.
     */
    protected int getKNN(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mapDefaultPreferences.get(MapDefaultPreferences.KNN)) : KNN;
    }

    /**
     * Method that allows to modify the preference node containing the value of K for K-NN.
     * This method is used only at the end of the validation phase, to set K to its optimal value.
     * 
     * @param K                 The new value for K, to substitute to the current value.
     */
    protected void setKNN(int K) {
        preferences.put(MapDefaultPreferences.KNN, String.valueOf(K));
        KNN = K;
    }

    /**
     * Method that returns the maximum allowed value of K for K-NN, dependant on the dimension of the training set.
     * This value is then used by yhe class {@link PreferencesDialog} during initialization of its GUI, to define
     * the property 'MaxValue' of the JSlider that controls the value of K-NN.
     * 
     * @return                  The maximum allowed value of K for K-NN.
     */
    protected int getKNNMaximum() {
        return trainingSetSize;
    }

    /**
     * Method that returns the minimum allowed value of K for K-NN. Like the previous method, this one is used
     * by the class {@link PreferencesDialog} during the initialization of its GUI, to define the property 'MinValue'
     * of the JSlider that controls the value of K-NN.
     * 
     * @return                  The minimum allowed value of K for K-NN.
     */
    protected int getKNNMinimum() {
        return 1;
    }

    /**
     * Method that returns the maximum allowed value of K for K-NN during K-Fold cross validation
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The maximum allowed value of K for K-NN during K-Fold cross validation.
     */
    protected int getMaximumKNNValidation(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mapDefaultPreferences.get(MapDefaultPreferences.MAXKNNVALIDATION)) : maximumKNNValidation;
    }

    /**
     * Method that returns a boolean value indicating if the log file must be overwritten every time it is opened
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value indicating if the log file must be overwritten every time it is opened.
     */
    protected boolean isOverwriteLogFile(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isOVERWRITELOGFILE)) : isOverwriteLogFile;
    }

    /**
     * Method that returns the reference to the preferences node used by the application.
     * Every class of the program that needs to access this node uses this method to get a reference to it.
     * 
     * @return                  The reference to the preferences node used by the application.
     */
    protected Preferences getPreferences() {
        return preferences;
    }

    /**
     * Method that returns the reference to the instance of the current scraper, used to extract the features
     * (the terms) from documents.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The reference to the instance of the current scraper.
     */
    protected Scraper getScraper(boolean defaultValue) {
        return (defaultValue) ? getScraperInstance(mapDefaultPreferences.get(MapDefaultPreferences.SCRAPER)) : Scraper;
    }

    /**
     * Method that returns an instance of the scraper whose complete class name is provided in input.
     * 
     * @param nomeClasse        The complete name of the class whose instance must be returned, and that
     *                          must implement the interface {@link Scraper}.
     * @return                  An instance of the class whose name has been specified in input.
     * @see                     Scraper
     */
    private Scraper getScraperInstance(String nomeClasse) {
        try {
            Class scraperCorrenteClass = Class.forName(nomeClasse);
            return (Scraper) scraperCorrenteClass.cast(scraperCorrenteClass.newInstance());
        } catch (Exception ex) {
            DocumentClassifierView.showErrorMessage(ex.toString());
            return null;
        }
    }

    /**
     * Method that returns the name of the package where all scrapers must be put, in order to be correctly
     * registered and used by the program.
     * 
     * @return                  The name of the package where all scrapers must be put.
     */
    protected String getScrapersPackageName() {
        return documentClassifierAppResources.getString("Application.scrapersPackageName");
    }

    /**
     * Method that returns the name of the package where all stemmers must be put, in order to be correctly
     * registered and used by the program.
     * 
     * @return                  The name of the package where all stemmers must be put.
     */
    protected String getStemmersPackageName() {
        return documentClassifierAppResources.getString("Application.stemmersPackageName");
    }

    /**
     * Method that returns a boolean value indicating if stemming of terms from documents is enabled or not.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value indicating if stemming of terms from documents is enabled or not.
     */
    protected boolean isStemming(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isSTEMMING)) : isStemming;
    }

    /**
     * Method that returns the complete name of the class which implements the current stemmer.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  The complete name of the class which implements the current stemmer.
     */
    protected String getStemmer(boolean defaultValue) {
        return (defaultValue) ? mapDefaultPreferences.get(MapDefaultPreferences.STEMMER) : currentStemmer;
    }

    /**
     * Method that returns a boolean value which indicates if the partitioning for K-Fold cross validation is stratified or not.
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value which indicates if the partitioning for K-Fold cross validation is stratified
     *                          or not.
     */
    protected boolean isStratified(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isSTRATIFIED)) : isStratified;
    }

    /**
     * Method that (Re)generates the training set from the specified input directory.
     * The documents inside this directory must be organized inside one or more subdirectories, one for each category of the training set,
     * and having the name of this.
     * It is possible to have documents that belong to more than one category at the same time, by putting a copy of the same document in every
     * subdirectory corresponding to one of the categories it belongs to.
     * This method is declared as 'synchronized', to forbid other threads from executing in parallel one of the other two synchronized methods
     * of this class, while the training set is being (re)generated.
     * 
     * @param   path        The path of the directory containing the training set.
     * @return              A boolean value indicating if the generation of the training set has completed without errors (true), or not (false).
     * @throws  Exception
     */
    protected synchronized boolean generateTrainingSet(String path) throws Exception {
        
        System.out.println("Check and generation of the training set...");
        
        File dir = new File(path);
        if (dir.isDirectory()) {
            /**
             * First of all, a new set made of subsets of documents is created, without overwriting the current training set.
             * If at the end of the method there hasn't been any error, the value of the variable which contains the reference to
             * the current training set will be changed with the reference to the new one.
             */
            Set<Set<Document>> copyTrainingSet = Collections.synchronizedSet(new LinkedHashSet<Set<Document>>());
            trainingSetSize = 0;
            for (File D : dir.listFiles()) {
                if (D.isDirectory() && D.list().length > 0) {
                    Set<Document> documentsCurrentCategory = new LinkedHashSet<Document>();
                    for (File F : D.listFiles(new FileFilter() {

                        public boolean accept(File arg) {
                            if (arg.isDirectory()) {
                                return false;
                            } else {
                                return true;
                            }
                        }
                    })) {
                        String[] titleText = readDocument(new FileInputStream(F));
                        String titolo = titleText[0];
                        String testo = titleText[1];
                        String category = D.getName();
                        /**
                         * A new instance of the class {@link Document} is created, which represents the current document read
                         * from the file system.
                         */
                        Document newDocument = new Document(titolo, testo, category, F.getCanonicalPath());
                        boolean addNewDocument = true;
                        /**
                         * If the same document (that is, a document with the same text and title) is already inside the training set,
                         * but in another category than the one corresponding to the current directory-->Instead of adding a duplicated
                         * instance of it in another category, the name of the current category is added to the set of categories of the
                         * document already present in the training set, and then a reference to its instance is added to the training set's
                         * category currently being inspected, creating in this way a graph structure (as opposed to a tree one, where
                         * every document can belong to one and only one category, which can be though as its father).
                         * So, to summarize, for each document of the training set there is only one instance, but this can be put in more
                         * than one category.
                         */
                        for (Set<Document> currentSubSet : copyTrainingSet) {
                            for (Document currentDocument : currentSubSet) {
                                if (currentDocument.equals(newDocument)) {
                                    currentDocument.addCategory(category);
                                    /**
                                     * The variable 'nuovoDocumento' contains the reference to the document already present in the training set.
                                     */
                                    newDocument = currentDocument;
                                    addNewDocument = false;
                                    break;
                                }
                            }
                        }
                        /**
                         * If I must add a new document to the training set-->I increment the variable that memorizes its total dimension.
                         */
                        if (addNewDocument) {
                            trainingSetSize++;
                        }
                        /**
                         * In any case, a reference to an instance of the class 'Document' (a new one, or an already existing one) is added
                         * to the current category.
                         */
                        documentsCurrentCategory.add(newDocument);
                    }
                    copyTrainingSet.add(documentsCurrentCategory);
                }
            }
            /**
             * I make sure that the training set cannot be modified from this moment on.
             */
            copyTrainingSet = Collections.unmodifiableSet(copyTrainingSet);
            /**
             * From this moment the variable 'trainingSet' points to the new training set just created.
             * The previous reference is then lost.
             */
            trainingSet = copyTrainingSet;

            System.out.println("Check and generation successfully completed");

            return true;
        } else {
            System.out.println("Check and generation aborted");
            return false;
        }
    }

    /**
     * This method returns a reference to the current training set.
     * It is declared as 'synchronized' to avoid that, if another thread is in the meantime executing one of the other two synchronized methods
     * of the class (for example if the training set is being regenerated), the reference to the current training set is concurrently accessed,
     * reference that for sure will change once the method {@link #generateTrainingSet generateTrainingSet} will finish, making the previous reference
     * invalid.
     * 
     * @return                  A reference to the current training set, or null if this hasn't yet been generated.
     */
    protected synchronized Set<Set<Document>> getTrainingSet() {
        return trainingSet;
    }

    /**
     * This method returns the total dimension of the current training set, that is the number of different documents
     * thereby present.
     * It is declared as 'synchronized' for the same reasons of the previous method.
     * 
     * @return                  The total dimension of the current training set, or 0 if this hasn't yet been created.
     */
    protected synchronized int getTrainingSetSize() {
        return trainingSetSize;
    }

    /**
     * This method returns a boolean value that indicates if the current document (its title and text) must be visualized
     * during the validation phase (keep in mind that this slows down its execution).
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value that indicates if the current document must be visualized during the validation phase.
     */
    protected boolean isVisualizeCurrentDocument(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isVISUALIZECURRENTDOCUMENT)) : isVisualizeCurrentDocument;
    }

    /**
     * This method returns a boolean value that indicates if the list of documents of the training set, ranked to the current
     * query document, must be visualized during the validation phase (keep in mind that this slows down its execution).
     * 
     * @param defaultValue      Boolean parameter that indicates if what must be returned is the default value (true)
     *                          or the actual one (false).
     * @return                  A boolean value that indicates if the list of documents of the training set, ranked to the current
     *                          query document, must be visualized during the validation phase.
     */
    protected boolean isVisualizeListRankedDocuments(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mapDefaultPreferences.get(MapDefaultPreferences.isVISUALIZELISTRANKEDDOCUMENTS)) : isVisualizeDocumentsList;
    }
}