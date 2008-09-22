
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
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.jdesktop.application.Action;
import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.Task;

/**
 * Classe principale dell'applicazione.
 * <p>
 * Oltre all'implementazione di alcuni metodi della classe {@link SingleFrameApplication},
 * che estende per personalizzarne il comportamento, questa classe contiene:
 * <p>
 *  - Un metodo ({@link #preferenceChange preferenceChange}) che implementa l'interfaccia {@link PreferenceChangeListener},
 * chiamato ogni volta che i valori di una o più preferenze vengono modificati (da metodi di questa o di altre classi dell'applicazione,
 * che registrano questa come PreferenceChangeListener)
 * <p>
 *  - Metodi per accedere ai valori delle varie preferenze in lettura
 * <p>
 *  - Un metodo ({@link #generaTrainingSet generaTrainingSet}) per (ri)generare il training set ogni volta che sia necessario
 * <p>
 *  - Un metodo ({@link #leggiDocumento leggiDocumenti}) per leggere il titolo ed il testo di un documento dal file system,
 * e da questi creare un'istanza della classe {@link Documento}
 * 
 * @author  Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 * @see     SingleFrameApplication
 */
public class DocumentClassifierApp extends SingleFrameApplication implements PreferenceChangeListener {
    
    /**
     * Variabile che contiene un riferimento al nodo delle preferenze dell'applicazione.
     * In questo caso, il riferimento è al nodo dell'utente che esegue il programma, che può essere
     * implementato in modo diverso a seconda della piattaforma in cui viene eseguito (per esempio
     * come file nascosto all'interno della directory $HOME in caso di sistemi Unix, o come chiavi
     * di registro in caso di sistemi Windows).
     * Tutti i metodi di questa o di altre classi che vogliono accedere in lettura o in scrittura
     * ai valori di tali preferenze, utilizzano i metodi {@link Preferences#get get} e {@link Preferences#put put}
     * di questa variabile, anzichè accedere direttamente al nodo a cui essa si riferisce.
     * In questo modo si è aggiunto un livello di astrazione all'accesso alle preferenze, e se si
     * dovesse decidere di cambiare il nodo delle preferenze contenente i valori d'interesse per
     * questa applicazione, basterà solo cambiare la riga sottostante.
     */
    private static final Preferences preferenze = Preferences.userRoot();
    //Mappa che associa al nome di ogni preferenza il suo valore di default
    private static final MappaPreferenzeDefault mappaPreferenzeDefault=new MappaPreferenzeDefault();
    /**
     * Il training set corrente dell'applicazione è rappresentato come un insieme di sottoinsiemi di documenti
     * (i sottoinsiemi sono formati da tutti i documenti appartenenti alla stessa categoria)
     */
    private Set<Set<Documento>> trainingSet;
    private int trainingSetSize = 0;
    //Variabili di configurazione, corrispondenti alle preferenze dell'applicazione
    private Scraper Scraper;
    private String trainingSetDirectory, logFile, listaStopWords,  metrica;
    private boolean isEliminazioneStopWords, isStemming, isStratificato, isLogging, isOverwriteLogFile, isVisualizzaDocumentoCorrente, isVisualizzaListaDocumenti;
    private String stemmerCorrente;
    private int KNN,  KFold, maximumKNNValidation;
    
    /**
     * Responsible for initializations that must occur before the GUI is constructed
     * by startup
     * 
     * @param args          The arguments passed to the application through the
     *                      command line
     */
    @Override
    protected void initialize(String[] args) {
        super.initialize(args);
        /*
         * Genero manualmente un PreferenceChangeEvent per forzare all'avvio del
         * programma la lettura delle preferenze (dall'apposito nodo, rappresentato
         * dalla variabile 'preferenze') e l'inizializzazione delle corrispondenti
         * variabili interne alla classe
         */
        preferenceChange(new PreferenceChangeEvent(preferenze, "All", "All"));
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
     * @return the instance of DocumentClassifierApp
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
     * Cancella tutti i valori delle preferenze, ripristinando di fatto i rispettivi valori di default
     */
    private void cancellaPreferenze() {
        try {
            preferenze.clear();
        } catch (BackingStoreException ex) {
            Logger.getLogger(DocumentClassifierApp.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        }
    }
    
    /**
     * Metodo che implementa l'interfaccia 'PreferenceChangeListener'.
     * <p>
     * Viene invocato ogni volta che una preferenza del nodo rappresentato dalla
     * variabile 'preferenze' viene modificata, cambiando il valore e/o
     * lo stato di uno o più componenti del JDialog {@link PreferenzeDialog}.
     * Quando una preferenza viene modificata, questo metodo aggiorna il valore
     * della corrispondente variabile all'interno di questa classe.
     *  
     * @param event             Il PreferenceChangeEvent da gestire
     */
    public void preferenceChange(PreferenceChangeEvent event) {
        try {
            String eventKey = event.getKey();
            String chiaveCorrente, valoreDefault;
            
            chiaveCorrente=MappaPreferenzeDefault.SCRAPER;
            Scraper=getScraperInstance(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.isELIMINAZIONESTOPWORDS;
            isEliminazioneStopWords = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.LISTASTOPWORDS;
            valoreDefault=mappaPreferenzeDefault.get(chiaveCorrente);
            listaStopWords = preferenze.get(chiaveCorrente, valoreDefault);
            if (!new File(listaStopWords).isFile()) {
                String message = "Il file specificato con la lista delle stopwords ("+listaStopWords+") non è valido,\n";
                listaStopWords = valoreDefault;
                if (new File(listaStopWords).isFile() && isEliminazioneStopWords) {
                    DocumentClassifierView.mostraMessaggioErrore(message + "usato il valore di default (" + listaStopWords + ")");
                } else if (!new File(listaStopWords).isFile() && isEliminazioneStopWords) {
                    DocumentClassifierView.mostraMessaggioErrore(message + "il file di default (" + listaStopWords + ") non è presente,\ndisattivata l'eliminazione delle stopwords");
                    isEliminazioneStopWords = false;
                    preferenze.put(MappaPreferenzeDefault.isELIMINAZIONESTOPWORDS, String.valueOf(isEliminazioneStopWords));
                }
            }
            
            chiaveCorrente=MappaPreferenzeDefault.isSTEMMING;
            isStemming = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.STEMMER;
            stemmerCorrente = preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente));
            
            //(Ri)Generazione training set
            chiaveCorrente=MappaPreferenzeDefault.TRAININGSET;
            trainingSetDirectory=preferenze.get(chiaveCorrente, valoreDefault);
            if (eventKey.equals("All") || eventKey.equals(MappaPreferenzeDefault.isSTEMMING) || eventKey.equals(MappaPreferenzeDefault.LISTASTOPWORDS)) {
                valoreDefault=mappaPreferenzeDefault.get(chiaveCorrente);
                if(!generaTrainingSet(trainingSetDirectory)) {
                    trainingSetDirectory=valoreDefault;
                    String message="Il percorso specificato per la directory del training set non è valido, ";
                    if(generaTrainingSet(trainingSetDirectory)) {
                        DocumentClassifierView.mostraMessaggioErrore(message + 
                                "usato il valore di default (" + trainingSetDirectory + ")");
                    }
                    else {
                        DocumentClassifierView.mostraMessaggioErrore(message +
                                "e la directory di default (" + trainingSetDirectory + ") non è presente.\n" +
                                "Impossibile eseguire la classificazione");
                    }
                }
            }
            
            chiaveCorrente=MappaPreferenzeDefault.METRICA;
            metrica = preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente));
            
            chiaveCorrente=MappaPreferenzeDefault.KNN;
            KNN = Integer.parseInt(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.MAXKNNVALIDATION;
            maximumKNNValidation = Integer.parseInt(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.KFOLD;
            KFold = Integer.parseInt(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.isSTRATIFICATO;
            isStratificato = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.isLOGGING;
            isLogging = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.LOGFILE;
            logFile = preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente));
            
            chiaveCorrente=MappaPreferenzeDefault.isOVERWRITELOGFILE;
            isOverwriteLogFile = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.isVISUALIZZADOCUMENTOCORRENTE;
            isVisualizzaDocumentoCorrente = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
            chiaveCorrente=MappaPreferenzeDefault.isVISUALIZZALISTADOCUMENTICORRELATI;
            isVisualizzaListaDocumenti = Boolean.parseBoolean(preferenze.get(chiaveCorrente, mappaPreferenzeDefault.get(chiaveCorrente)));
            
        } catch (Exception ex) {
            DocumentClassifierView.mostraMessaggioErrore(ex.toString());
            cancellaPreferenze();
            ex.printStackTrace();
        }
    }
    
    /**
     * Legge da un InputStream (FileInputStream, NetworkInputStream, ecc..) il titolo
     * ed il testo della notizia associata ad esso, attraverso un apposito parser, e
     * li restituisce dentro un vettore di stringhe (posizione 0: Titolo, posizione 1: Testo).
     * 
     * @param  IS               L'InputStream da cui leggere la notizia
     * @return                  Il titolo ed il testo della notizia estratta, all'interno di un vettore di 2 stringhe
     * @throws Exception 
     */
    protected String[] leggiDocumento(InputStream IS) throws Exception {
        return Scraper.getDocumento(IS);
    }
    
    /**
     * Restituisce la lista delle classi presenti all'interno del package fornito in input.
     * 
     * @param pckgname          Il nome del package di cui si vogliono conoscere tutte le classi
     * @return                  La lista delle classi (sotto forma di istanze della classe {@link Class})
     *                          presenti all'interno del package specificato
     * @throws java.lang.ClassNotFoundException
     */
    protected static List<Class> getClassesForPackage(String pckgname) throws ClassNotFoundException {
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
            Enumeration<URL> resources = cld.getResources(pckgname.replace('.', '/'));
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                if (res.getProtocol().equalsIgnoreCase("jar")) {
                    JarURLConnection conn = (JarURLConnection) res.openConnection();
                    JarFile jar = conn.getJarFile();
                    for (JarEntry e : Collections.list(jar.entries())) {

                        if (e.getName().startsWith(pckgname.replace('.', '/')) && e.getName().endsWith(".class") && !e.getName().contains("$")) {
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
            throw new ClassNotFoundException(pckgname + " does not appear to be " +
                    "a valid package (Null pointer exception)");
        } catch (UnsupportedEncodingException encex) {
            throw new ClassNotFoundException(pckgname + " does not appear to be " +
                    "a valid package (Unsupported encoding)");
        } catch (IOException ioex) {
            throw new ClassNotFoundException("IOException was thrown when trying " +
                    "to get all resources for " + pckgname);
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
                        classes.add(Class.forName(pckgname + '.' + file.substring(0, file.length() - 6)));
                    }
                }
            } else {
                throw new ClassNotFoundException(pckgname + " (" + directory.getPath() +
                        ") does not appear to be a valid package");
            }
        }
        return classes;
    }
    
    /**
     * Metodi di accesso in lettura alle variabili interne alla classe, ciascuna corrispondente
     * ad una delle preferenze. Ciascuno di questi metodi prende in input un valore booleano,
     * che indica se al chiamante deve essere restituito il valore di default della corrispondente
     * variabile, oppure il suo valore attuale
     */
    
    /**
     * Restituisce il percorso della directory del training set
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il percorso della directory del training set
     */
    protected String getDirectoryTrainingSet(boolean defaultValue) {
        return (defaultValue) ? mappaPreferenzeDefault.get(MappaPreferenzeDefault.TRAININGSET) : trainingSetDirectory;
    }
    
    /**
     * Restituisce un valore booleano che indica se l'eliminazione delle stopwords da ogni documento è attivata oppure no
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se l'eliminazione delle stopwords da ogni documento è attivato oppure no
     */
    protected boolean isEliminazioneStopWords(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isELIMINAZIONESTOPWORDS)): isEliminazioneStopWords;
    }
    
    /**
     * Restituisce il percorso del file contenente la lista delle stopwords
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il percorso del file contenente la lista delle stopwords
     */
    protected String getListaStopWords(boolean defaultValue) {
        return (defaultValue) ? mappaPreferenzeDefault.get(MappaPreferenzeDefault.LISTASTOPWORDS) : listaStopWords;
    }
    
    /**
     * Restituisce il percorso del file di log
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il percorso del file di log
     */
    protected String getLogFile(boolean defaultValue) {
        return (defaultValue) ? mappaPreferenzeDefault.get(MappaPreferenzeDefault.LOGFILE) : logFile;
    }
    
    /**
     * Restituisce un valore booleano che indica se il logging dei messaggi in fase di validazione è attivato o no
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se il logging dei messaggi in fase di validazione è attivato o no
     */
    protected boolean isLogging(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isLOGGING)) : isLogging;
    }
    
    /**
     * Restituisce il riferimento alla mappa dei nomi->valori di default delle preferenze del programma
     * 
     * @return                  Il riferimento alla mappa dei nomi->valori di default delle preferenze del programma
     */
    protected MappaPreferenzeDefault getMappaPreferenze() {
        return mappaPreferenzeDefault;
    }
    
    /**
     * Restituisce il nome della metrica correntemente utilizzata per calcolare la distanza tra ogni documento del training set ed il documento query
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il nome della metrica corrente
     */
    protected String getMetrica(boolean defaultValue) {
        return (defaultValue) ? mappaPreferenzeDefault.get(MappaPreferenzeDefault.METRICA) : metrica;
    }
    
    /**
     * Restituisce il valore corrente di K per la K-Fold cross validation
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il valore corrente di K per la K-Fold cross validation
     */
    protected int getKFold(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mappaPreferenzeDefault.get(MappaPreferenzeDefault.KFOLD)): KFold;
    }
    
    /**
     * Restituisce il valore massimo consentito di K per la K-Fold cross validation, dipendente dalla dimensione
     * del training set.
     * <p>
     * Tale valore è utilizzato dalla classe {@link PreferenzeDialog} in fase di creazione della
     * propria interfaccia grafica, per definire la proprietà 'MaxValue' del JSlider associato al valore di K-Fold.
     * 
     * @return                  Il valore massimo consentito di K per la K-Fold cross validation
     */
    protected int getKFoldMaximum() {
        return trainingSetSize;
    }
    
    /**
     * Restituisce il valore minimo consentito di K per la K-Fold cross validation.
     * <p>
     * Analogamente al precedente metodo, anche questo è utilizzato dalla classe {@link PreferenzeDialog}
     * in fase di creazione della propria interfaccia grafica, per definire la proprietà 'MinValue' del JSlider
     * associato al valore di K-Fold.
     * 
     * @return                  Il valore minimo consentito di K per la K-Fold cross validation
     */
    protected int getKFoldMinimum() {
        return 2;
    }
    
    /**
     * Restituisce il valore corrente di K per il K-NN
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il valore corrente di K per il K-NN
     */
    protected int getKNN(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mappaPreferenzeDefault.get(MappaPreferenzeDefault.KNN)) : KNN;
    }
    
    /**
     * Permette di modificare la preferenza relativa al valore di K per il K-NN.
     * Questo metodo è utilizzato solo alla fine della fase di validazione, per settare K al valore ottimale
     * 
     * @param K                 Il nuovo valore di K da sostituire al valore corrente
     */
    protected void setKNN(int K) {
        preferenze.put(MappaPreferenzeDefault.KNN, String.valueOf(K));
        KNN=K;
        //preferenceChange(new PreferenceChangeEvent(preferenze, MappaPreferenzeDefault.KNN, stringK));
    }
    
    /**
     * Restituisce il valore massimo consentito di K per il K-NN, dipendente dalla dimensione del training set.
     * Tale valore è utilizzato dalla classe {@link PreferenzeDialog} in fase di creazione della propria interfaccia
     * grafica, per definire la proprietà 'MaxValue' del JSlider associato al valore di K-NN.
     * 
     * @return                  Il valore massimo consentito di K per il K-NN
     */
    protected int getKNNMaximum() {
        return trainingSetSize;
    }
    
    /**
     * Restituisce il valore minimo consentito di K per il K-NN. Analogamente al precedente metodo, anche questo
     * è utilizzato dalla classe {@link PreferenzeDialog} in fase di creazione della propria interfaccia grafica,
     * per definire la proprietà 'MinValue' del JSlider associato al valore di K-NN.
     * 
     * @return                  Il valore minimo consentito di K per il K-NN
     */
    protected int getKNNMinimum() {
        return 1;
    }
    
    /**
     * Restituisce il valore corrente di K per il K-NN durante la fase di validazione
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true),
     *                          oppure il suo valore attuale (false)
     * @return                  Il valore corrente di K per il K-NN durante la fase di validazione
     */
    protected int getMaximumKNNValidation(boolean defaultValue) {
        return (defaultValue) ? Integer.parseInt(mappaPreferenzeDefault.get(MappaPreferenzeDefault.MAXKNNVALIDATION)) : maximumKNNValidation;
    }
    
    /**
     * Restituisce un valore booleano che indica se il file di log deve essere sovrascritto ad ogni apertura o no
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true),
     *                          oppure quello attuale (false)
     * @return                  Un valore booleano che indica se il file di log deve essere sovrascritto ad ogni apertura (true) o no (false)
     */
    protected boolean isOverwriteLogFile(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isOVERWRITELOGFILE)) : isOverwriteLogFile;
    }
    
    /**
     * Restituisce il riferimento al nodo delle preferenze utilizzato dall'applicazione.
     * Qualunque classe del programma che necessita di accedere a tale nodo utilizza questo metodo per
     * ottenerne un riferimento
     * 
     * @return                  Il riferimento al nodo delle preferenze utilizzato dall'applicazione
     */
    protected Preferences getPreferenze() {
        return preferenze;
    }
    
    /**
     * Restituisce l'istanza dello scraper corrente, utilizzato per estrarre le features (i termini) dai documenti
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  L'istanza dello scraper corrente
     */
    protected Scraper getScraper(boolean defaultValue) {
        return (defaultValue) ? getScraperInstance(mappaPreferenzeDefault.get(MappaPreferenzeDefault.SCRAPER)) : Scraper;
    }
    
    /**
     * Restituisce un istanza dello scraper il cui nome completo viene fornito in input.
     * 
     * @param nomeClasse        Il nome completo della classe di cui bisogna restituire un'istanza, e che
     *                          deve implementare l'interfaccia {@link Scraper}
     * @return                  Un'istanza della classe specificata in input
     * @see                     Scraper
     */
    private Scraper getScraperInstance(String nomeClasse) {
        try {
            Class scraperCorrenteClass = Class.forName(nomeClasse);
            return (Scraper) scraperCorrenteClass.cast(scraperCorrenteClass.newInstance());
        } catch (Exception ex) {
            DocumentClassifierView.mostraMessaggioErrore(ex.toString());
            ex.printStackTrace();
            return null;
        }
    }
    
    /**
     * Restituisce il nome del package in cui devono trovarsi tutti gli scraper, per poter essere
     * correttamente riconosciuti dal programma
     * 
     * @return                  Il nome del package in cui devono trovarsi tutti gli scraper
     */
    protected String getScrapersPackageName() {
        return "documentclassifier.Scrapers";
    }
    
    /**
     * Restituisce il nome del package in cui devono trovarsi tutti gli stemmers, per poter essere
     * correttamente riconosciuti dal programma
     * 
     * @return                  Il nome del package in cui devono trovarsi tutti gli stemmers
     */
    protected String getStemmersPackageName() {
        return "org.tartarus.snowball.ext";
    }
    
    /**
     * Restituisce un valore booleano che indica se lo stemming dei termini dei documenti è attivato o no
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se lo stemming dei termini dei documenti è attivato o no
     */
    protected boolean isStemming(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isSTEMMING)) : isStemming;
    }
    
    /**
     * Restituisce il nome della classe che implementa lo stemmer corrente
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Il nome completo della classe che implementa lo stemmer corrente
     */
    protected String getStemmer(boolean defaultValue) {
        return (defaultValue) ? mappaPreferenzeDefault.get(MappaPreferenzeDefault.STEMMER) : stemmerCorrente;
    }
    
    /**
     * Restituisce un valore booleano che indica se il partizionamento per la K-Fold cross validation è stratificato o no
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se il partizionamento per la K-Fold cross validation è stratificato o no
     */
    protected boolean isStratificato(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isSTRATIFICATO)) : isStratificato;
    }
    
    /**
     * (Ri)Genera il training set a partire dalla directory.
     * I documenti all'interno di tali directory devono essere organizzati all'interno di sottodirectory, una per ogni categoria del training set,
     * avente appunto il nome della stessa. E' possibile avere documenti che appartengono a più di una categoria, inserendo una copia in ogni sottodirectory
     * corrispondente alla categoria di appartenenza.
     * Il metodo è dichiarato come 'synchronized' per evitare che altri thread possano eseguire parallelamente uno degli altri 2 metodi
     * synchronized di questa classe, per esempio se il training set viene rigenerato
     * 
     * @param percorso          Il percorso della directory da cui creare il training set
     * @return                  Un valore booleano che indica se la creazione del training set è andata a buon fine (true), oppure se ci sono stati errori (false)
     * @throws java.lang.Exception
     */
    @Action(block = Task.BlockingScope.APPLICATION)
    public synchronized boolean generaTrainingSet(String percorso) throws Exception {
        File dir = new File(percorso);
        if (dir.isDirectory()) {
            /**
             * Innanzitutto viene creato un nuovo insieme di insiemi di documenti, senza andare a sovrascrivere il training set correte.
             * Se alla fine del flusso di esecuzione del metodo non ci saranno stati errori, il riferimento della variabile che rappresenta
             * il training set corrente verrà sostituito con il riferimento al nuovo training set
             */
            Set<Set<Documento>> copiaTrainingSet = Collections.synchronizedSet(new LinkedHashSet<Set<Documento>>());
            trainingSetSize = 0;
            for (File D : dir.listFiles()) {
                if (D.isDirectory() && D.list().length > 0) {
                    Set<Documento> notizieCategoriaCorrente = new LinkedHashSet<Documento>();
                    for (File F : D.listFiles(new FileFilter() {

                        public boolean accept(File arg) {
                            /**
                             * Se ci sono directory (per esempio contenenti le immagini delle pagine web) non voglio che vengano
                             * prese in considerazione, altrimenti il parser cercherebbe di leggerle, generando un'eccezzione
                             */
                            if (arg.isDirectory()) {
                                return false;
                            } else {
                                return true;
                            }
                        }
                    })) {
                        String[] titoloTesto = leggiDocumento(new FileInputStream(F));
                        String titolo=titoloTesto[0];
                        String testo=titoloTesto[1];
                        String categoria=D.getName();
                        /**
                         * Viene creata una nuova istanza della classe {@link Documento}, rappresentante il documento corrente
                         * letto dal file system
                         */
                        Documento nuovoDocumento = new Documento(titolo,testo,categoria,F.getCanonicalPath());
                        boolean aggiungiNuovoDocumento=true;
                        /**
                         * Se però lo stesso documento (cioè un documento con lo stesso titolo e testo) si trova già nel training set,
                         * in un'altra categoria rispetto a quella della directory corrente-->Invece di aggiungere un duplicato di esso
                         * in un'altra categoria, si aggiunge il nome della categoria corrente all'insieme delle categorie del documento
                         * già presente nel training set, e poi si aggiunge alla categoria corrente un riferimento al documento (unico)
                         * già presente nel training set, realizzando così una struttura a grafo.
                         * Di ogni documento viene quindi memorizzata una sola istanza, ma ci possono essere più riferimento ad essa nelle
                         * varie categorie
                         */
                        for(Set<Documento> sottoInsiemeCorrente : copiaTrainingSet) {
                            for (Documento documentoCorrente : sottoInsiemeCorrente) {
                                if (documentoCorrente.equals(nuovoDocumento)) {
                                    documentoCorrente.addCategoria(categoria);
                                    /**
                                     * La variabile 'nuovoDocumento' viene fatta puntare al documento già presente nel training set
                                     */
                                    nuovoDocumento=documentoCorrente;
                                    aggiungiNuovoDocumento=false;
                                    break;
                                }
                            }
                        }
                        /**
                         * Se devo aggiungere un nuovo documento al training set-->Incremento la variabile che ne memorizza la dimensione
                         */
                        if(aggiungiNuovoDocumento) {
                            //documento=new Documento(titolo, testo, categoria, F.getCanonicalPath());
                            trainingSetSize++;
                        }
                        /**
                         * In ogni caso aggiungo un riferimento ad un'istanza della classe documento (nuovo, oppure già presente nel training set)
                         * al sottoinsieme di documenti della categoria corrente
                         */
                        notizieCategoriaCorrente.add(nuovoDocumento);
                    }
                    copiaTrainingSet.add(notizieCategoriaCorrente);
                }
            }
            /**
             * Mi assicuro che il training set non possa essere modificato da questo momento in poi
             */
            copiaTrainingSet=Collections.unmodifiableSet(copiaTrainingSet);
            /**
             * Da questo momento la variabile 'trainingSet' punta al nuovo training set appena creato.
             * Il riferimento precedente viene quindi perso
             */
            trainingSet = copiaTrainingSet;
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Restituisce un riferimento al training set corrente.
     * Il metodo è dichiarato come 'synchronized' per evitare che, se un altro thread sta nel frattempo eseguendo uno degli altri 2 metodi synchronized,
     * per esempio se il training set viene rigenerato, si possa accedere parallelamente al riferimento al training set corrente, che sicuramente cambierà
     * una volta che il metodo @see 'generaTrainingSet' avrà finito la sua esecuzione, dando così risultato ad apparenti incongruenze nella classificazione
     * dei documenti
     * 
     * @return                  Un riferimento al training set corrente, oppure null se non è stato ancora creato
     */
    protected synchronized Set<Set<Documento>> getTrainingSet() {
        return trainingSet;
    }
    
    /**
     * Restituisce la dimensione del training set.
     * Il metodo è dichiarato come 'synchronized' per evitare che, se un altro thread sta nel frattempo eseguendo uno degli altri 2 metodi synchronized,
     * per esempio se il training set viene rigenerato, si possa accedere parallelamente alla dimensione del training set corrente, che potrebbe non essere
     * coerente con la dimensione del training set in fase di generazione, dando così risultato ad apparenti incongruenze nella classificazione
     * dei documenti
     * 
     * @return     La dimensione del training set corrente, oppure 0 se non è stato ancora creato
     */
    protected synchronized int getTrainingSetSize() {
        return trainingSetSize;
    }
    
    /**
     * Restituisce un valore booleano che indica se il documento corrente deve essere visualizzato
     * durante la fase di validazione (ne rallenta l'esecuzione)
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se il documento corrente deve essere visualizzato
     *                          durante la fase di validazione
     */
    protected boolean isVisualizzaDocumentoCorrente(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isVISUALIZZADOCUMENTOCORRENTE)) : isVisualizzaDocumentoCorrente;
    }
    
    /**
     * Restituisce un valore booleano che indica se la lista dei documenti correlati al documento query corrente,
     * ordinati in base alla distanza, deve essere visualizzata durante la fase di validazione (ne rallenta l'esecuzione)
     * 
     * @param defaultValue      Se deve essere restituito il valore di default di questa variabile (true)
     *                          oppure il suo valore attuale (false)
     * @return                  Un valore booleano che indica se la lista dei documenti correlati al documento query corrente
     *                          deve essere visualizzata in fase di validazione
     */
    protected boolean isVisualizzaListaDocumenti(boolean defaultValue) {
        return (defaultValue) ? Boolean.parseBoolean(mappaPreferenzeDefault.get(MappaPreferenzeDefault.isVISUALIZZALISTADOCUMENTICORRELATI)) : isVisualizzaListaDocumenti;
    }
    
}