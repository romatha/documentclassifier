
package documentclassifier.Metriche;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Questa classe implementa la distanza di Bhattacharrya, usando come funzione peso dei singoli termini di ogni documento (rispetto alla query)
 * la frequenza relativa, già calcolata in fase di creazione dell'istogramma di ogni documento, nel relativo costruttore.
 * <p>
 * Essa implementa l'interfaccia {@link Comparator}, su elementi di tipo Entry<Map<String,Object>, Map<String, Double>>, rappresentanti singoli documenti
 * all'interno di una {@link LinkedList}. In questo caso il Comparator ordina gli elementi della lista in base alla distanza di Bhattacharrya rispetto
 * al documento query.
 * 
 * @author  Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 * @see     Comparator
 */
public class DistanzaBhattacharryaComparator implements Comparator<Entry<Map<String,Object>,Map<String,Double>>> {
    
    /**
     * Metodo che implementa l'interfaccia Comparator, per confrontare due documenti
     * di cui è stata calcolata la distanza di Bhattacharrya, rispetto ad un documento
     * query di riferimento.
     * <p>
     * Il confronto avviene quindi utilizzando tale valore, calcolato in precedenza usando il metodo statico {@link #calcolaDistanza calcolaDistanza},
     * e restituendo di conseguenza 1, 0 o -1.
     * <p>
     * Questo metodo viene chiamato per ogni possibile coppia di documenti di una collezione.
     * 
     * @param A             Il 1° documento da confrontare
     * @param B             Il 2° documento da confrontare
     * @return              Un intero tra i 3 possibili valori -1, 0 o 1, rappresentante la relazione di ordinamento tra il 1° ed il 2° documento in input
     */
    public int compare(Entry<Map<String,Object>,Map<String,Double>> A, Entry<Map<String,Object>,Map<String,Double>> B) {
        
        double distanzaA=(Double) A.getKey().get("Distanza");
        double istanzaB=(Double) B.getKey().get("Distanza");
        if( distanzaA > istanzaB ) {
            /**
             * L'ordinamento deve essere crescente (distanza maggiore-->dissimilarità maggiore-->Viene dopo)
             */
            return 1;
        }
        else if ( distanzaA == istanzaB ) {
            return 0;
        }
        else {
            return -1;
        }
    }
    
    /**
     * Metodo statico per il calcolo della distanza di Bhattacharrya tra ogni documento (rappresentato da un vettore che associa
     * ai suoi termini le rispettive frequenze) di un certo insieme (denominato documentSet), ed un documento di riferimento
     * (denominato 'query')
     * 
     * @param documentSet   Insieme di documenti, rappresentato da una lista di istanze della classe Entry<Map<String,Object>, Map<String, Double>>,
     *                      la cui chiave è una mappa {@link String}->{@link Object} con informazioni sul documento, ed il cui valore è una mappa
     *                      'String'->'Double' che rappresenta l'istogramma dei termini del documento (dopo il pre-processing del testo)
     * @param query         Documento query rispetto al quale tutti le distanze dei documenti del document set vengono calcolate.
     *                      Il documento è rappresentato solo dal suo istogramma, implementato da una mappa che associa chiavi di tipo {@link String}
     *                      a elementi di tipo {@link Double}.
     *                      Non è necessario quindi che esso sia accompagnato da una mappa {@link String}->{@link Object}, contenente informazioni sul
     *                      documento stesso
     */
    public static void calcolaDistanza(LinkedList<Entry<Map<String, Object>, Map<String, Double>>> documentSet, Map<String, Double> query) {
        
        Map<String,Double> istogrammaCorrente;
        Set<String> intersezione;
        /**
         * Per ogni documento del document set viene calcolato il prodotto scalare di tale documento con
         * il documento query, ma prendendo come valori delle singole componenti dei rispettivi istogrammi
         * (le frequenze dei termini) le radici quadrate.
         * Alla fine del calcolo, si applica la formula della distanza di Bhattacharrya: d(A,B)=sqrt(1-prodottoscalare)
         */
        for(Entry<Map<String,Object>,Map<String,Double>> documentoCorrente : documentSet ) {
            
            double Somma=0.0;
            istogrammaCorrente=documentoCorrente.getValue();
            /**
             * Viene creato l'insieme intersezione tra il documento corrente ed il documento query,
             * perchè è uno spreco in termini computazionali tentare di calcolare il prodotto scalare
             * prendendo in considerazione anche termini non comuni tra i due, in quanto essi danno
             * contributo 0 al risultato finale
             */
            intersezione=new HashSet<String>();
            intersezione.addAll(istogrammaCorrente.keySet());
            intersezione.retainAll(query.keySet());
            for ( String Token : intersezione ) {
                Somma = Somma + (Math.sqrt(query.get(Token) * istogrammaCorrente.get(Token)));
            }
            documentoCorrente.getKey().put("Distanza", new Double(Math.sqrt(1-Somma)));
        }
    }
    
//    public static double calcolaDistanza(Map<String,Double> documentoCorrente, Map<String,Double> query) {
//        double Somma=0;
//        Set<String> intersezione=new HashSet<String>(documentoCorrente.keySet());
//        intersezione.retainAll(query.keySet());
//        for ( String Token : intersezione ) {
//            Somma = Somma + (query.get(Token) * documentoCorrente.get(Token));
//        }
//        return Math.sqrt(1-Math.sqrt(Somma));
//    }
}
