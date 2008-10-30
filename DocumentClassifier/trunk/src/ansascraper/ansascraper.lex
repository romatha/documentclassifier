package ansascraper;

import java_cup.*;
import java_cup.runtime.*;
import java.io.*;

%%
%{
    private String title = "", news = "", link = "";                    //Strings that are set by the lexical analyzer during the reading of the HTML page, and passed as tokens.
    private int tablesCounter = 0, cellsCounter = 0, spanCounter = 0;   //Variables that control if the lexical analyzer is currently inside equal tags, one inside each other.
    private boolean readNews = false;
    /**
     * 2-columns matrix containing, for each sequence corresponding to a special HTML character, the graphical rappresentation of such character.
     * It is used before we pass the token representing the text of a news to the syntax analyzer, to make sure that the text of the token doesn't contain
     * sequences of the format &[qualcosa];
     */
    private String specialCharacters[][] = {
        {"\"", "&quot;"},
        {"\"","&quot;"},
        {"'","&apos;"},
        {"&","&amp;"},
        {"<","&lt;"},
        {">","&gt;"},
        {" ","&nbsp;"},
        {"¤","&curren;"},
        {"¢","&cent;"},
        {"£","&pound;"},
        {"¥","&yen;"},
        {"¦","&brvbar;"},
        {"§","&sect;"},
        {"©","&copy;"},
        {"ª","&ordf;"},
        {"«","&laquo;"},
        {"¬","&not;"},
        {"­","&shy;"},
        {"®","&reg;"},
        {"™","&trade;"},
        {"¯","&macr;"},
        {"°","&deg;"},
        {"±","&plusmn;"},
        {"¹","&sup1;"},
        {"²","&sup2;"},
        {"³","&sup3;"},
        {"´","&acute;"},
        {"µ","&micro;"},
        {"¶","&para;"},
        {"·","&middot;"},
        {"¸","&cedil;"},
        {"º","&ordm;"},
        {"»","&raquo;"},
        {"¼","&frac14;"},
        {"½","&frac12;"},
        {"¾","&frac34;"},
        {"¿","&iquest;"},
        {"×","&times;"},
        {"÷","&divide;"},
        {"à","&agrave;"},
        {"á","&aacute;"},
        {"â","&acirc;"},
        {"ã","&atilde;"},
        {"ä","&auml;"},
        {"å","&aring;"},
        {"æ","&aelig;"},
        {"ç","&ccedil;"},
        {"è","&egrave;"},
        {"é","&eacute;"},
        {"ê","&ecirc;"},
        {"ë","&euml;"},
        {"ì","&igrave;"},
        {"í","&iacute;"},
        {"î","&icirc;"},
        {"ï","&iuml;"},
        {"ð","&eth;"},
        {"ñ","&ntilde;"},
        {"ò","&ograve;"},
        {"ó","&oacute;"},
        {"õ","&otilde;"},
        {"ø","&oslash;"},
        {"ù","&ugrave;"},
        {"ú","&uacute;"},
        {"û","&ucirc;"},
        {"ü","&uuml;"},
        {"ý","&yacute;"},
        {"þ","&thorn;"},
        {"ÿ","&yuml;"},
        {"Œ","&OElig;"},
        {"œ","&oelig;"},
        {"Š","&Scaron;"},
        {"š","&scaron;"},
        {"Ÿ","&Yuml;"},
        {"ˆ","&circ;"},
        {"˜","&tilde;"},
        {"–","&ndash;"},
        {"—","&mdash;"},
        {"‘","&lsquo;"},
        {"’","&rsquo;"},
        {"‚","&sbquo;"},
        {"„","&bdquo;"},
        {"†","&dagger;"},
        {"‡","&Dagger;"},
        {"…","&hellip;"},
        {"‰","&permil;"},
        {"‹","&lsaquo;"},
        {"›","&rsaquo;"},
        {"€","&euro;"},
    };
    
    /**
     * This method substitutes the special HTML characters eventually present in the input string
     * with their corresponding visual representation.
     * To do so it uses the matrix <code>specialCharacters</code>.
     * @param  input   String to normalize
     * @return         Normalized string
     */
    private String replaceSpecialCharacters(String input) {
        for (int i = 0; i < specialCharacters.length; i++) {
            input = input.replaceAll(specialCharacters[i][1], specialCharacters[i][0]);
        }
        return input;
    }
%}

%eofval{
    return new Symbol(sym.EOF);
%eofval}

%ignorecase
%unicode
%cup
%state SCRAPING,SCRAPINGTITLE,SCRAPINGDOCUMENT,SCRAPINGLINKDOCUMENT
Letter=[A-Za-z]
Digit=[0-9]
SpecialSymbol=[" \"'!=@/\[]#.:_()-&;?"]
Name={Letter}({Letter}|{Digit}|"-")*                    #A name (of a tag or an attribute) is made of a starting letter, followed by 0 or more letters, numbers or '-'
Word={Name}                                             #'Word' is synonim of 'Name'
Value=(\")?({Letter}|{Digit}|{SpecialSymbol})*(\")?     #A value is made of 0 or more letters, digits or special symbols, optionally included between double quotes (")
Attribute={Name}"="({Value})                            #An attribute is made of a name, a sign '=', e da un valore
Tag=<("/")?{Name}(" ")*({Attribute})*(" ")*("/")?>      #Regular expression that represents a generic HTML tag
OpeningTable=<table(" ")*({Attribute})*(" ")*>          #Regular expression that represents an opening table tag (<table ...>)
OpeningCell=<td(" ")*({Attribute})*(" ")*>              #Regular expression that represents an opening cell tag (<td ...>)
OpeningSpan=<span(" ")*({Attribute})*(" ")*>            #Regular expression that represents an opening span tag (a section of text: <span ...>)
Link=<a(" ")*({Attribute})*(" ")*>                      #Regular expression that represents a generic HTML tag
Image=<img(" ")*({Attribute})*(" ")*("/")?>             #Regular expression that represents an image tag (<img ...>)
br=<br(" ")*("/")?(" ")*>                               #Regular expression that represents a carriage return tag (<br/>)
p=<p(" ")*({Attribute})*(" ")*>                         #Regular expression that represents an opening paragraph tag (<p ...>)
DELIM=[ \n\r\t\f]                                       #Regular expression that represents a delimiter character
%%

<YYINITIAL> {OpeningTable}
{
    if(yytext().contains("content_table"))
        yybegin(SCRAPING);
}

<SCRAPING>  {OpeningTable}   { tablesCounter++; }

<SCRAPING>  {OpeningCell}
{
    if(yytext().contains("content_title_primopiano"))
    {
        title="";
        yybegin(SCRAPINGTITLE);
    }
    else if(yytext().contains("content_text_news") && !readNews)
    {
        news="";
        yybegin(SCRAPINGDOCUMENT);
    }
}

<SCRAPING> {OpeningSpan}
{
    if(yytext().contains("content_text_news")  && !readNews)
        yybegin(SCRAPINGDOCUMENT);
}

<SCRAPING>   "</table>"
{
    if(tablesCounter==-1)
        yybegin(YYINITIAL);
    else
        tablesCounter--;
}

<SCRAPINGTITLE,SCRAPINGDOCUMENT>    {OpeningCell} { cellsCounter++; }

<SCRAPINGTITLE>  "</td>"
{
    if(cellsCounter==0)
    {
        title=replaceSpecialCharacters(title);
        yybegin(SCRAPING);
        return new Symbol(sym.TITLE,title.trim());
    }
    else
        cellsCounter--;
}

<SCRAPINGTITLE>    .   { title+=yytext(); }

<SCRAPINGDOCUMENT>   {OpeningSpan}  { spanCounter++; }

<SCRAPINGDOCUMENT>  "</span>"
{
    if(spanCounter==0)
    {
        news=replaceSpecialCharacters(news);
        yybegin(SCRAPING);
        readNews=true;
        return new Symbol(sym.TEXT,news.trim().replaceFirst(" {72,}",""));
    }
    else
        spanCounter--;
}

<SCRAPINGDOCUMENT>  "</td>"
{
    if(cellsCounter==0)
    {
        news=replaceSpecialCharacters(news);
        yybegin(SCRAPING);
        readNews=true;
        return new Symbol(sym.TEXT,news.trim().replaceFirst(" {72,}",""));
    }
    else
        cellsCounter--;
}

<SCRAPINGDOCUMENT>  {Link}
{
    link="";
    yybegin(SCRAPINGLINKDOCUMENT);
}

<SCRAPINGDOCUMENT>  {br}|{p}  { news+=System.getProperty("line.separator"); }

<SCRAPINGDOCUMENT> . { news+=yytext(); }

<SCRAPINGLINKDOCUMENT>   "</a>"
{
    news+=link;
    yybegin(SCRAPINGDOCUMENT);
}

<SCRAPINGLINKDOCUMENT>   {Tag}   { }

<SCRAPINGLINKDOCUMENT>  .   { link+=yytext(); }

{DELIM}|.|{Tag} { }
