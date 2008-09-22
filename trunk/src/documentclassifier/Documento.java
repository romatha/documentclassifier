
package documentclassifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.tartarus.snowball.SnowballProgram;

/**
 * Questa classe rappresenta un documento, caratterizzato da un titolo, dal testo,
 * dalle categorie di appartenenza, dalla URL (percorso locale o di rete) da cui è possibile reperirlo,
 * e dall'istogramma dei suo termini
 *
 * @author "Salvo Danilo Giuffrida", giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com
 */
class Documento {
    
    private String titolo,testo,percorso;
    /**
     * Un documento può anche appartenere a più categorie, se sue copie sono presenti
     * in più sottodirectory del training set. A questo scopo, le categorie a cui
     * ogni documento appertiene sono rappresentate da stringhe all'interno di uno
     * insieme (classe Set<String> del Collections framework)
     */
    private Set<String> categorie;
    /**
     * L'istogramma di ogni documento è rappresentato da una mappa avente come chiavi
     * le stringhe dei singoli termini, e come valore di ogni chiave la frequenza relativa
     * di quel termine all'interno del documento
     */
    private Map<String,Double> istogramma;
    
    /**
     * Crea un nuovo documento che ha il titolo, il testo, la categoria iniziale ed il
     * percorso di lettura specificati.
     * <p>
     * Questo costruttore viene chiamato ogni qualvolta si deve (ri)generare il training set corrente,
     * in quanto in questo caso si conoscono le categorie di appartenenza dei documenti ivi presenti.
     * Ulteriori categorie potranno successivamente essere aggiunte chiamando il metodo {@link #addCategoria addCategoria}.
     * 
     * @param titolo            Il titolo del documento
     * @param testo             Il testo completo del documento
     * @param categoriaIniziale Una delle categorie a cui appartiene il documenti
     * @param percorso          La URL (percorso locale o di rete) da cui è possibile leggere il file originale
     *                          da cui viene creata questa istanza
     */
    public Documento(String titolo, String testo, String categoriaIniziale, String percorso) {
        try {
            this.titolo = titolo;
            this.testo = testo;
            this.categorie=new HashSet<String>();
            categorie.add(categoriaIniziale);
            this.percorso = percorso;
            /**
             * Al momento della creazione del documento, l'istogramma dei suoi termini viene generato,
             * per poter poi essere utilizzato dalla metrica corrente, per calcolare la distanza tra
             * questo documento e la query.
             * Nel caso l'utente decida di attivare/disattivare lo stemming e/o l'eliminazione delle
             * stopwords, nonchè cambiare il file contenente la lista delle stesse, l'intero training set
             * viene rigenerato, in quanto l'istogramma di ciascun suo documento deve essere ricalcolato,
             * in funzione del fatto che termini che prima venivano eliminati in fase di pre-processing
             * del testo, come le stopwords, adesso potrebbero invece essere presi in considerazione, e
             * viceversa
             */
            this.istogramma = creaIstogramma();
        } catch (Exception ex) {
            DocumentClassifierView.mostraMessaggioErrore(ex.toString());
        }
    }
    
    /**
     * Crea un nuovo documento di cui però non si conosce ancora la categoria di appartenenza.
     * Questo costruttore è usato per creare un'istanza che rappresenti un documento query, di cui
     * cioè deve essere determinata la categoria di appartenenza
     * 
     * @param titolo            Il titolo del documento
     * @param testo             Il testo completo del documento
     * @param percorso          La URL (percorso locale o di rete) da cui è possibile leggere il file
     *                          originale da cui viene creata questa istanza
     */
    public Documento(String titolo, String testo, String percorso) {
        this(titolo,testo,null,percorso);
    }
    
    /**
     * Crea un nuovo documento di cui però si conosce solo il titolo.
     * Questo costruttore è usato per creare un'istanza che rappresenti una query come in un motore
     * di ricerca, da cui devono essere restituiti i documenti più rilevanti rispetto ai termini specificati.
     * 
     * @param   query            La query da sottoporre al sistema d'information retrieval
     */
    public Documento(String query) {
        this(query,"",null);
    }
    
    /**
     * Crea l'istogramma della notizia, se necessario pre-processando il testo
     * (tramite stemming ed eliminazione delle stopwords) prima di calcolare
     * la frequenza dei suoi termini
     * 
     * @return                  L'istogramma dei termini della notizia, rappresentato
     *                          da una mappa avente chiavi (i termini) di tipo String,
     *                          e valori (frequenze relative) di tipo Double
     */
    private Map<String, Double> creaIstogramma() throws Exception {
        
        DocumentClassifierApp application=DocumentClassifierApp.getApplication();
        boolean isStemming=application.isStemming(false);
        boolean isEliminazioneStopWords=application.isEliminazioneStopWords(false);
        
        /**
         * Anche se l'utente ha scelto di non applicare lo stemming ai documenti,
         * è necessario creare le classi di cui sotto, altrimenti il compilatore
         * segnala un errore
         */
        Class stemClass = Class.forName(application.getStemmer(false));
        SnowballProgram stemmer = (SnowballProgram) stemClass.newInstance();
        @SuppressWarnings("unchecked")
        Method stemMethod = stemClass.getMethod("stem", new Class[0]);
        
        Object[] emptyArgs = new Object[0];
        String Punteggiatura = " \t\n\r\f,;.:!'\"()?[]=-@";
        
        Map<String, Double> istogrammaDocumento = new HashMap<String, Double>();
        String tokenCorrente;
        Double frequenza;
        int Peso;
        String riga;
        
        /**
         * Costruisco l'insieme delle stopwords, leggendo l'apposito file
         */
        Set<String> listaStopWords = new HashSet<String>();
        BufferedReader StopWordsBR;
        File listaCorrenteStopWordsFile=new File(DocumentClassifierApp.getApplication().getListaStopWords(false));
        String[] Campi;
        if(isEliminazioneStopWords) {
            /**
             * Se l'utente ha scelto di non attivare l'eliminazione delle stopwords,
             * non viene aggiunto nessun elemento all'insieme, che rimane vuoto
             */
            StopWordsBR = new BufferedReader(new FileReader(listaCorrenteStopWordsFile));
            while ((riga = StopWordsBR.readLine()) != null) {
                if (!riga.isEmpty()) {
                    Campi = riga.split("|");
                    if (!Campi[0].startsWith(" ")) {
                        listaStopWords.add(Campi[0].trim());
                    }
                }
            }
            StopWordsBR.close();
        }
        
        /**
         * Fase di pre-processing del testo.
         * Rappresento il titolo ed il testo del documento come due stringhe appartenenti
         * ad un array, per poter applicare le stesse operazioni ad entrambi i suoi elementi,
         * ma pesando in modo diverso i termini a seconda se appartengono al titolo o al testo
         */
        String[] titoloTesto = new String[2];
        titoloTesto[0] = titolo;
        titoloTesto[1] = testo;
        for (int j = 0; j <= 1; j++) {
            if (j == 0) {
                /**
                 * Se sto leggendo il titolo del documento-->I termini che lo costituiscono hanno peso
                 * doppio rispetto ai termini del testo, in quanto il titolo è maggiormente identificativo
                 * dell'argomento
                 */
                Peso = 2;
            } else {
                Peso = 1;
            }
            titoloTesto[j] = titoloTesto[j].toLowerCase();
            StringTokenizer ST = new StringTokenizer(titoloTesto[j], Punteggiatura);
            while (ST.hasMoreTokens()) {
                tokenCorrente = ST.nextToken();
                /**
                 * Eliminazione delle stopwords (se attivata dall'utente) e dei numeri (in ogni caso).
                 * Il termine 'eliminazione' è improprio, in quanto ciò che viene realmente fatto è
                 * semplicemente non prendere in considerazione un termine se esso è presente nella lista
                 * delle stopwords o rappresenta un numero, e non aggiungerlo alla mappa che rappresenta
                 * l'istogramma
                 */
                if (!listaStopWords.contains(tokenCorrente) && !tokenCorrente.matches("\\d+")) {
                    
                    if(isStemming) {
                        /**
                         * Stemming sul termine corrente: Lo stemmer crea un nuovo termine contenente
                         * la radice di quello passato in input
                         */
                        stemmer.setCurrent(tokenCorrente);
                        stemMethod.invoke(stemmer, emptyArgs);
                        tokenCorrente = stemmer.getCurrent();
                    }
                    /**
                     * La frequenza del termine corrente (eventualmente ridotto alla radice) viene letta
                     * dall'istogramma ed aggiornata
                     */
                    frequenza = istogrammaDocumento.get(tokenCorrente);
                    if (frequenza == null) {
                        frequenza = 0.0;
                    }
                    istogrammaDocumento.put(tokenCorrente, frequenza + Peso);
                }
            }
        }
        
        /**
         * L'istogramma è stato completato, ora bisogna normalizzarne le frequenze
         * rispetto alla lunghezza del documento (dopo il pre-processing)
         */
        double fattoreNormalizzazione = 0;
        for (double I : istogrammaDocumento.values()) {
            //Calcolo della lunghezza del documento
            fattoreNormalizzazione += I;
        }
        //Normalizzazione delle frequenze (da assolute a relative)
        for (String Token : istogrammaDocumento.keySet()) {
            istogrammaDocumento.put(Token, istogrammaDocumento.get(Token) / fattoreNormalizzazione);
        }
        /**
         * Assicuro che, una volta creato, l'istogramma di questo documento non possa
         * più essere modificato (accidentalmente o intenzionalmente) da altre classi che
         * vi accedono in lettura tramite il metodo accessorio {@link #getIstogramma getIstogramma}.
         */
        return Collections.unmodifiableMap(istogrammaDocumento);
    }
    
    /**
     * Metodo accessorio per leggere l'istogramma del documento
     * 
     * @return                  L'istogramma del documento
     */
    public Map<String, Double> getIstogramma() {
        return istogramma;
    }
    
    /**
     * Metodo accessorio per leggere l'insieme dei nomi delle categorie a cui
     * questo documento appartiene
     * 
     * @return                  Insieme con i nomi delle categorie a cui questo documento appartiene
     */
    public Set<String> getCategorie() {
        return categorie;
    }
    
    /**
     * Aggiunge il nome di una categoria all'insieme, se essa non è già presente
     * 
     * @param categoria         Il nome della categoria da aggiungere
     */
    public void addCategoria(String categoria) {
        categorie.add(categoria);
    }
    
    /**
     * Metodo accessorio per leggere la URL da cui è possibile reperire il documento
     * originale rappresentato da questa istanza (file PDF, HTML, ecc...)
     * 
     * @return                  La URL (percorso locale o di rete) da cui è possibile reperire
     *                          il documento originale rappresentato da questa istanza
     */
    public String getPercorso() {
        return percorso;
    }
    
    /**
     * Metodo accessorio per leggere il testo del documento
     * 
     * @return                  Il testo del documento rappresentato da questa istanza
     */
    public String getTesto() {
        return testo;
    }
    
    /**
     * Metodo accessorio per leggere il titolo del documento
     * 
     * @return                  Il titolo del documento rappresentato da questa istanza
     */
    public String getTitolo() {
        return titolo;
    }
    
    /**
     * Due documenti sono uguali se hanno lo stesso titolo e lo stesso testo
     * 
     * @param obj
     * @return                  Un valore booleano che indica se il documento fornito in input al metodo
     *                          è uguale a quello rappresentato da questa istanza
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Documento other = (Documento) obj;
        if (this.titolo != other.titolo && (this.titolo == null || !this.titolo.equals(other.titolo))) {
            return false;
        }
        if (this.testo != other.testo && (this.testo == null || !this.testo.equals(other.testo))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (this.titolo != null ? this.titolo.hashCode() : 0);
        hash = 71 * hash + (this.testo != null ? this.testo.hashCode() : 0);
        return hash;
    }
    
}
