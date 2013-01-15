
package documentclassifier.Scrapers;

import ansascraper.*;
import documentclassifier.DocumentClassifierView;
import java.io.InputStream;

/**
 * Scraper that is able to get the title and text from web pages of the ANSA's web site (www.ansa.it)
 * that contain single news.
 * 
 * @author      Salvo Danilo Giuffrida (salvod.giuffrida@gmail.com)
 */
public class ANSAScraper implements Scraper {
    
    public String[] getDocument(InputStream IS) {
        try {
            parser myparser = new parser(new Yylex(IS));
            String[] titleText = ((String) myparser.parse().value).split(System.getProperty("line.separator"), 2);
            return titleText;
        } catch (Exception ex) {
            DocumentClassifierView.showErrorMessage(ex.toString());
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "ANSA.it";
    }
    
}
