
package documentclassifier.Scrapers;

import ansascraper.*;
import documentclassifier.DocumentClassifierView;
import java.io.InputStream;

/**
 * Scraper che riesce ad estrarre il titolo ed il testo da pagine web del sito dell'ANSA (www.ansa.it)
 * contenenti singole notizie.
 * 
 * @author  Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public class ANSAScraper implements Scraper {
    
    public String[] getDocumento(InputStream IS) {
        try {
            parser mioparser = new parser(new Yylex(IS));
            String[] TitoloTesto = ((String) mioparser.parse().value).split(System.getProperty("line.separator"), 2);
            return TitoloTesto;
        } catch (Exception ex) {
            DocumentClassifierView.mostraMessaggioErrore(ex.toString());
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "ANSA.it";
    }
    
}
