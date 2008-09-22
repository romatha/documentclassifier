
package documentclassifier;

import java.util.HashMap;

/**
 * Lo scopo di questa classe è di mantenere in modo strutturato due tipi di informazioni
 * per le altre classi del programma:
 * 1 - I nomi delle varie preferenze
 * 2 - I rispettivi valori di default
 * 
 * In questo modo viene aggiungo un livello di astrazione per le altre classi del programma,
 * che devono far riferimento ai nomi e/o ai valor di default delle varie preferenze,
 * in quanto esse utilizzano i campi statici di tipo stringa di questa classe per riferirsi
 * ai nomi delle varie preferenze, piuttosto che direttamente ad essi.
 * Analogamente, per modifcare i valori di default delle varie preferenze è necessario
 * modificare semplicemente una riga del costruttore di questa classe
 * 
 * @author Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public class MappaPreferenzeDefault extends HashMap<String,String> {
    
    private static final DocumentClassifierApp application=DocumentClassifierApp.getApplication();
    /**
     * Questa classe non deve essere serializzata, quindi il valore assegnato
     * al campo 'serialVersionUID' non è importante
     */
    private static final long serialVersionUID=0;
    
    private static final char pathSeparator='/';
    /**
     * Seguono i nomi delle varie preferenze
     */
    /**
     * Lo scraper corrente, per estrarre le features (i termini) dai documenti
     * del training set e dal documento query
     */
    public static final String SCRAPER="Scraper";
    /**
     * Se l'eliminazione delle stopwords da ogni documento è attivata oppure no
     */
    public static final String isELIMINAZIONESTOPWORDS="isStopWords";
    /**
     * Il percorso del file contente la lista delle stopwords da eliminare da
     * ogni documento
     */
    public static final String LISTASTOPWORDS="StopWords";
    /**
     * Se lo stemming dei termini di ogni documento è attivato o no
     */
    public static final String isSTEMMING="isStemming";
    /**
     * Il nome della classe che implementa lo stemmer da applicare
     */
    public static final String STEMMER="Stemmer";
    /**
     * La metrica corrente da usare per calcolare la distanza tra il documento
     * query ed ogni documento del training set
     */
    public static final String METRICA="Metrica";
    /**
     * Il valore di K per il K-NN, usato in fase di classificazione per determinare
     * la categoria più frequente tra i K documenti più vicini al documento query
     * (secondo la metrica corrente), e quindi assegnarla al documento query
     */
    public static final String KNN="KNN";
    /**
     * Il massimo valore di K per il K-NN durante la fase di validazione, durante
     * la quale la K-Fold cross validation viene ripetuta con differenti valori
     * di K-NN, da 1 fino al massimo scelto dall'utente
     */
    public static final String MAXKNNVALIDATION="MaxKNN";
    /**
     * Il valore di K per la K-Fold cross validation, cioè il numero di partizioni
     * in cui suddividere il training set (casuali o stratificate), e che a turno
     * vengono prese in considerazione come control set rispetto al resto del training
     * set
     */
    public static final String KFOLD="KFOLD";
    /**
     * Se la stratificazione per le partizioni create durante la fase di validazione è
     * attivata o meno. Per stratificazione s'intende che ogni partizione contiene lo
     * stesso numero di notizie da ogni categoria; a parità di valore di K-Fold, la
     * stratificazione riduce l'errore di classificazione, in quanto il training set
     * rispetto a cui ogni control set viene classificato è perfettamente bilanciato,
     * poichè contiene per ogni categoria lo stesso numerp di campioni
     */
    public static final String isSTRATIFICATO="isStratificato";
    /**
     * Se il logging della fase di validazione è abilitato o no
     */
    public static final String isLOGGING="isLogging";
    /**
     * Il percorso del file di log
     */
    public static final String LOGFILE="LogFile";
    /**
     * Se il file di log deve essere sovrascritto ad ogni apertura o no
     */
    public static final String isOVERWRITELOGFILE="isOverwriteLogFile";
    /**
     * Se, durante la fase di validazione del classificatore, la URL, il titolo ed il testo
     * del documento corrente devono essere visualizzati nell'interfaccia grafica
     */
    public static final String isVISUALIZZADOCUMENTOCORRENTE="isVisualizzaDocumentoCorrente";
    /**
     * Se, durante la fase di validazione del classificatore, la lista dei documenti del
     * training set corrente, ordinata in base alla distanza dal documento query corrente,
     * deve essere visualizzata o meno
     */
    public static final String isVISUALIZZALISTADOCUMENTICORRELATI="isVisualizzaInfoDocumentiCorrelati";
    /**
     * Il percorso della directory del training set
     */
    public static final String TRAININGSET="Directory training set";
    
    /**
     * Crea una nuova mappa dei nomi e dei valori di default delle preferenze del programma
     */
    public MappaPreferenzeDefault() {
        
        super();
        /**
         * Dapprima viene determinato il percorso della directory da cui il programma viene eseguito,
         * in quanto di default si assume che la directory del training set si trovi in una sottodirectory
         * 'TrainingSet' della directory principale del programma
         */
        String programParentDir=getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        programParentDir=programParentDir.substring(0, programParentDir.lastIndexOf(pathSeparator)+1);
        /**
         * La mappa dei nomi delle preferenze viene inizializzata con i corrispondenti valori di default
         */
        put(TRAININGSET,programParentDir+"TrainingSet");
        /**
         * Lo scraper di default è quello per il sito dell'ANSA ({@link ANSAScraper}),
         * l'unico per ora disponibile,  all'interno del package {@link documentclassifier.Scrapers}
         */
        put(SCRAPER, application.getScrapersPackageName()+".ANSAScraper");
        /**
         * La lista delle stopwords di default è quella dell'Italiano
         */
        put(LISTASTOPWORDS,programParentDir+"StopWords"+pathSeparator+"Italian.txt");
        /**
         * Di default l'eliminazione delle stopwords da ogni documento è attivata
         */
        put(isELIMINAZIONESTOPWORDS, "true");
        /**
         * Di default lo stemming dei termini di ogni documento è attivoato
         */
        put(isSTEMMING, "true");
        /**
         * Lo stemmer di default è quello per la lingua italiana, all'interno del package
         * 'org.tartarus.snowball.ext'
         */
        put(STEMMER, application.getStemmersPackageName()+".italianStemmer");
        /**
         * La metrica di default per calcolare la distanza tra i documenti è la TF-IDF
         */
        put(METRICA, "TF-IDF");
        /**
         * Il valore di default per il K-NN è '1'
         */
        put(KNN, "1");
        /**
         * Il valore massimo di default per il KNN durante la fase di validazione è
         * uguale al valore di default di KNN, cioè 1
         */
        put(MAXKNNVALIDATION,get(KNN));
        /**
         * Il valore di K per la K-Fold cross-validation è di default 10, in quanto
         * è il valore maggiormente utilizzato, essendo un buon compromesso tra
         * accuratezza e velocità
         */
        put(KFOLD, "10");
        /**
         * Il partizionamento stratificato per la K-Fold cross validation è di default
         * disattivato
         */
        put(isSTRATIFICATO,"false");
        /**
         * Di default il logging dei messaggi durante la fase di validazione è disattivato
         * (per ragioni di performance)
         */
        put(isLOGGING,"false");
        /**
         * Il percorso di default del file di log (se il logging è attivato) è './LogFile.log'
         */
        put(LOGFILE,programParentDir+"LogFile.log");
        /**
         * Di default il file di log viene sovrascritto ad ogni apertura (no append)
         */
        put(isOVERWRITELOGFILE,"false");
        /**
         * La visualizzazione delle informazioni sul documento corrente, durante
         * la fase di validazione, è di default disattivata, per ragioni di performance
         */
        put(isVISUALIZZADOCUMENTOCORRENTE,"false");
        /**
         * La visualizzazione dei documenti del training set corrente, ordinati rispetto
         * alla distanza dalla query corrente, durante la fase di validazione è di default
         * disattivata, per ragioni di performance
         */
        put(isVISUALIZZALISTADOCUMENTICORRELATI,"false");
    }
    
}
