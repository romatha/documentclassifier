
package documentclassifier.Scrapers;

import java.io.InputStream;

/**
 * Interfaccia che ogni scraper deve implementare per poter essere utilizzato con l'applicazione.
 * 
 * @author  Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public interface Scraper {
    
    /**
     * Metodo che prende in input un InputStream da cui leggere un documento,
     * e restituisce in un array di stringhe il titolo (in posizione 0) ed il testo del documento (in posizione 1)
     * 
     * @param   IS              L'InputStream da cui leggere il documento
     * @return                  Un array di 2 stringhe contenenti rispettivamente il titolo
     *                          ed il testo del documento
     */
    public String[] getDocumento(InputStream IS);
    
    /**
     * Restituisce una breve descrizione di questo scraper (non più di una riga), come per esempio
     * il nome del sito web a cui si riferisce (ANSA.it, Repubblica.it, ecc...), od il tipo di file
     * da cui riesce ad estrarre il testo (file PDF, Word, ODT, ecc...)
     * 
     * @return                  Una stringa che descrive questo scraper
     */
    @Override
    public String toString();
    
}
