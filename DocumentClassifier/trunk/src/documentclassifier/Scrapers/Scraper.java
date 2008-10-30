
package documentclassifier.Scrapers;

import java.io.InputStream;

/**
 * Interface that every scraper must implement.
 * 
 * @author      Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public interface Scraper {
    
    /**
     * Method that takes in input an InputStream, reads a document from it, and returns an array of strings,
     * with its title (position 0) and text (position 1).
     * 
     * @param   IS              The InputStream from where reading a document.
     * @return                  An array of two strings, containing respectively the document's title and text.
     */
    public String[] getDocument(InputStream IS);
    
    /**
     * Method that returns a short description of this scraper (not longer than one row), like for example
     * the name of the web site it refers to (ANSA.it, CNN.com, etc...), or the type of file it is able to
     * scrape (HTML, PDF, Word, ODT, etc...).
     * 
     * @return                  A short description of this scraper.
     */
    @Override
    public String toString();
    
}
