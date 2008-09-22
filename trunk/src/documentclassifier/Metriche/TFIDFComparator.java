
package documentclassifier.Metriche;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Questa classe calcola i valori della metrica TF-IDF (Term Frequency-Inverse Document Frequency), da utilizzare all'interno del VSM (Vector Space Model).
 * <p>
 * Essa implementa l'interfaccia {@link Comparator}, su elementi di tipo Entry<Map<String,Object>, Map<String, Double>>, rappresentanti singoli documenti
 * all'interno di una {@link LinkedList}. In questo caso il Comparator ordina gli elementi della lista in base al coseno dell'angolo tra ciascuno di essi
 * ed il documento query.
 * 
 * @author Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 * @see Comparator
 */
public class TFIDFComparator implements Comparator<Entry<Map<String,Object>, Map<String, Double>>> {
    
    /**
     * Metodo che implementa l'interfaccia Comparator, per confrontare due documenti
     * di cui sono stati calcolati i valori TF-IDF di ogni singolo termine, rispetto
     * ad un istogramma query.
     * <p>
     * Il confronto avviene quindi utilizzando il coseno dell'angolo tra ognuno di essi
     * e l'istogramma query, e restituendo di conseguenza 1, 0 o -1.
     * <p>
     * Questo metodo viene chiamato per ogni possibile coppia di istogrammi di una collezione.
     * <p>
     * I valori di TF-IDF ed i coseni vengono calcolati prima, usando il metodo statico
     * {@link #calcolaCoseno calcolaCoseno}.
     * 
     * @param A             Il 1° documento da confrontare
     * @param B             Il 2° documento da confrontare
     * @return              Un intero tra i 3 possibili valori -1, 0 o 1, rappresentante la relazione di ordinamento tra il 1° ed il 2° documento in input
     */
    public int compare(Entry<Map<String,Object>, Map<String, Double>> A, Entry<Map<String,Object>, Map<String, Double>> B) {
        
        /**
         * Per prima cosa leggo il valore del coseno di ciascuno dei due documenti, rispetto al documento query.
         * Esso è stato calcolato in precedenza dal metodo statico {@link #calcolaCoseno calcolaCoseno}, ed inserito
         * nella mappa rappresentante la chiave di ogni entry (contente informazioni sul documento a cui si riferisce),
         * alla voce "Distanza"
         */
        double cosA = (Double) A.getKey().get("Distanza");
        double cosB = (Double) B.getKey().get("Distanza");
        if (cosA > cosB) {
            /**
             * L'ordinamento deve essere decrescente (coseno maggiore-->documento più simile-->Viene prima)
             */
            return -1;
        } else if (cosA == cosB) {
            return 0;
        } else {
            return 1;
        }
    }
    
    /**
     * Metodo statico per il calcolo del coseno dell'angolo tra ogni documento (rappresentato da un vettore che associa
     * ai suoi termini le rispettive frequenze) di un certo insieme (denominato documentSet), ed un documento di
     * riferimento, denominato "query".
     * 
     * @param documentSet   Insieme di documenti, rappresentato da una lista di istanze della classe Entry<Map<String,Object>, Map<String, Double>>,
     *                      la cui chiave è una mappa {@link String}->{@link Object} con informazioni sul documento, ed il cui valore è una mappa
     *                      {@link String}->{@link Double} che rappresenta l'istogramma dei termini del documento (dopo il pre-processing del testo)
     * @param query         Documento query rispetto al quale tutti i coseni dei documenti del document set vengono calcolati.
     *                      Il documento è rappresentato solo dal suo istogramma, implementato da una mappa che associa chiavi di tipo {@link String}
     *                      a valori di tipo {@link Double}.
     *                      Non è necessario quindi che esso sia accompagnato da una mappa {@link String}->{@link Object}, contenente informazioni sul
     *                      documento stesso
     */
    public static void calcolaCoseno(LinkedList<Entry<Map<String, Object>, Map<String, Double>>> documentSet, Map<String, Double> query) {
        
        /**
         * Prima di calcolare i coseni, bisogna calcolare i valori di TF-IDF per ogni termine di ogni pagina/istogramma del document set.
         * La parte 'TF' del calcolo, cioè la frequenza relativa di ogni termine all'interno di ogni documento, è già stata calcolata
         * in fase di creazione dell'istogramma, durante la creazione di ogni istanza della classe {@link Documento}.
         * N.B.: La query deve entrare a far parte temporaneamente del document set per poter calcolare i valori di TF-IDF di ogni suo termine
         */
        
        /**
         * Per prima cosa creo un'istanza della classe 'Entry<Map<String,Object>, Map<String, Double>>',
         * che rappresenta il documento query, allo scopo di poterlo aggiungere temporaneamente al document set
         */
        Entry<Map<String,Object>, Map<String, Double>> entryQuery=new AbstractMap.SimpleEntry<Map<String,Object>, Map<String, Double>>(null,query);
        documentSet.add(entryQuery);
        double TFIDF;
        /**
         * Creo una copia della lista fornita in input a questo metodo, che memorizzerà gli istogrammi di ogni documento,
         * ma aggiornati con i valori della metrica 'TF-IDF' per ogni termine, anzichè la frequenza relativa.
         * Tutto ciò è necessario per evitare di modificare in modo permanente gli istogrammi originali dei documenti del document set,
         * cosa che porterebbe ad avere risultati diversi ad ogni invocazione (pur mantenendo costanti i parametri del metodo)
         */
        List<Entry<Map<String,Object>,Map<String,Double>>> copiaDocumentSet=new LinkedList<Entry<Map<String, Object>, Map<String, Double>>>();
        Map<String, Double> istogrammaDocumentoCorrente;
        /**
         * La mappa denominata 'DF' serve per memorizzare, come suggerisce il nome, la parte 'DF' di ogni termine j incontrato
         * in ogni istogramma, cioè il numero di documenti del document set che lo contengono (Dj), diviso il numero totale
         * di documenti del document set.
         * Utilizzando una mappa evito di dover ricalcolare questo numero ogni volta che incontro un certo termine in un documento:
         * mi basta invece calcolarlo la prima volta che incontro ogni termine all'interno del document set, e poi riutilizzarlo
         * ogni volta che sarà necessario
         */
        Map<String, Integer> IDF = new HashMap<String, Integer>();
        Integer numeroDocumentiTermineCorrente;
        /**
         * La variabile 'numeroTotaleDocumenti' contiene la dimensione del document set (N)
         */
        int numeroTotaleDocumenti = documentSet.size();
        /**
         * Per ogni documento del document set
         */
        for(Entry<Map<String,Object>,Map<String,Double>> entryCorrente : documentSet) {
            //istogrammaCorrente = documentSet.get(i).getValue();
            /**
             * Viene creata una copia del suo istogramma, che verrà modificata sostituendo al valore della
             * frequenza relativa di ogni termine il valore della sua TF-IDF
             */
            istogrammaDocumentoCorrente=new HashMap<String, Double>();
            istogrammaDocumentoCorrente.putAll(entryCorrente.getValue());
            for (String Token : istogrammaDocumentoCorrente.keySet()) {
                if (IDF.get(Token) == null) {
                    /**
                     * Il termine corrente non è mai stato incontrato finora-->La sua Document Frequency
                     * non è ancora presente nella mappa-->Conto il numero di occorrenze di tale termine
                     * nell'intero document set, lo divido per la dimensione del document set, e memorizzo
                     * tale valore nella mappa, in modo che dalla prossima volta in poi sia sufficiente
                     * accedere ad essa (in tempo costante) per conoscere la Document Frequency di questo termine
                     */
                    numeroDocumentiTermineCorrente = 0;
                    for (int j = 0; j < numeroTotaleDocumenti; j++) {
                        if (documentSet.get(j).getValue().get(Token) != null) {
                            numeroDocumentiTermineCorrente++;
                        }
                    }
                    IDF.put(Token, numeroTotaleDocumenti / numeroDocumentiTermineCorrente);
                }
                TFIDF = istogrammaDocumentoCorrente.get(Token) * Math.log10(IDF.get(Token));
                istogrammaDocumentoCorrente.put(Token, TFIDF);
            }
            copiaDocumentSet.add(new AbstractMap.SimpleEntry<Map<String,Object>,Map<String,Double>>(entryCorrente.getKey(),istogrammaDocumentoCorrente));
            if(entryCorrente==entryQuery) {
                entryQuery.setValue(istogrammaDocumentoCorrente);
            }
        }
        documentSet.remove(entryQuery);
        /**
         * Devo decrementare la variabile contenente il numero totale di documenti del document set,
         * perchè nel frattempo ho eliminato da esso la query, che avevo temporaneamente aggiunto all'inizio
         */
        numeroTotaleDocumenti--;
        /**
         * Calcolo del coseno di ogni documento rispetto a alla query
         */
        double normaDueQuery = normaDue(entryQuery.getValue());
        for (int i = 0; i < numeroTotaleDocumenti; i++) {
            istogrammaDocumentoCorrente = copiaDocumentSet.get(i).getValue();
            double distanza=prodottoScalare(entryQuery.getValue(), istogrammaDocumentoCorrente) / (normaDue(istogrammaDocumentoCorrente) * normaDueQuery);
            documentSet.get(i).getKey().put("Distanza", distanza);
            documentSet.get(i).getKey().put("Angolo",Math.toDegrees(Math.acos(distanza)));
        }
    }
    
    /**
     * Metodo per calcolare il prodotto scalare tra 2 vettori (di double), rappresentati in questo caso 
     * da due mappe {@link String}->{@link Double}.
     * 
     * @param A             Il 1° vettore
     * @param B             Il 2° vettore
     * @return              Il risultato del prodotto scalare tra A e B
     */
    private static double prodottoScalare(Map<String, Double> A, Map<String, Double> B) {
        
        double prodotto = 0.0;
        Set<String> intersezione = new HashSet<String>();
        intersezione.addAll(A.keySet());
        /**
         * Viene creato l'insieme intersezione tra A e B, perchè è uno spreco in termini computazionali
         * tentare di calcolare il prodotto scalare prendendo in considerazione anche termini non comuni
         * tra i due, in quanto essi danno contributo 0 al risultato finale
         */
        if (A != B) {
            intersezione.retainAll(B.keySet());
        }
        for (String token : intersezione) {
            prodotto += (A.get(token) * B.get(token));
        }
        return prodotto;
    }
    
    /**
     * Metodo per calcolare la norma-2 di un vettore, rappresentati in questo caso da due mappe {@link String}->{@link Double}.
     * 
     * @param V             Vettore di cui calcolare la norma-22
     * @return              Norma-2
     */
    private static double normaDue(Map<String, Double> V) {
        return Math.sqrt(prodottoScalare(V, V));
    }
}
