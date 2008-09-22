package ansascraper;

import java_cup.*;
import java_cup.runtime.*;
import java.io.*;

%%
%{
    private String TestoTitolo="",TestoNotizia="",TestoLink=""; //Stringhe che vengono riempite dall'analizzatore lessicale durante la lettura della pagina HTML, e man mano passate come token
    private int ContaTabelle=0,ContaCelle=0,ContaSpan=0;        //Variabili che controllano l'entrata in tag uguali innestati uno dentro l'altro
    private boolean NotiziaLetta=false;
    /**
    *   Matrice a 2 colonne contenente per ogni sequenza corrispondente ad un carattere speciale dell'HTML, la rappresentazione grafica di tale carattere.
    *   Viene utilizzata prima di passare il token rappresentante il testo di una notizia all'analizzatore sintattico, affinche' il testo di tale token non contenga sequenza del tipo &[qualcosa];
    */
    private String CaratteriSpeciali[][]=
    {   {"\"","&quot;"},
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
    *   Sostituisce gli eventuali caratteri speciali HTML presenti nella stringa in input con la loro versione visuale.
    *   Per fare cio' utilizza la matrice <code>CaratteriSpeciali</code>.
    *   @param  input   Stringa da normalizzare
    *   @return         Stringa normalizzata
    */
    private String SostituisciCaratteriSpeciali(String input)
    {
        for(int i=0;i<CaratteriSpeciali.length;i++)
            input=input.replaceAll(CaratteriSpeciali[i][1],CaratteriSpeciali[i][0]);
        return input;
    }
%}

%eofval{
    return new Symbol(sym.EOF);
%eofval}

%ignorecase
%unicode
%cup
%state SCRAPING,SCRAPINGTITOLO,SCRAPINGNOTIZIA,SCRAPINGLINKNOTIZIA
Lettera=[A-Za-z]
Cifra=[0-9]
SimboloSpeciale=[" \"'!=@/\[]#.:_()-&;?"]
Nome={Lettera}({Lettera}|{Cifra}|"-")*                  #Un nome (di un tag o di un attributo) e' formato una lettera iniziale, seguita da 0 o piu' lettere, numeri o '-'
Parola={Nome}                                           #'Parola' e' sinonimo di 'Nome'
Valore=(\")?({Lettera}|{Cifra}|{SimboloSpeciale})*(\")? #Un valore e' formato da 0 o piu' lettere, cifre o simboli speciali, opzionalmente (consigliato) inclusi tra doppie virgolette (")
Attributo={Nome}"="({Valore})                           #Un attributo e' formato da un nome, da un segno di '=', e da un valore
Tag=<("/")?{Nome}(" ")*({Attributo})*(" ")*("/")?>      #Espressione regolare che rappresenta un tag HTML generico
AperturaTabella=<table(" ")*({Attributo})*(" ")*>       #Espressione regolare che rappresenta un tag di apertura tabella (<table ...>)
AperturaSpan=<span(" ")*({Attributo})*(" ")*>           #Espressione regolare che rappresenta un tag di apertura di una sezione di testo (<span ...>)
Link=<a(" ")*({Attributo})*(" ")*>                      #Espressione regolare che rappresenta un tag HTML generico
Immagine=<img(" ")*({Attributo})*(" ")*("/")?>          #Espressione regolare che rappresenta un tag di immagine (<img ...>)
AperturaCella=<td(" ")*({Attributo})*(" ")*>            #Espressione regolare che rappresenta un tag di apertura di una cella di una tabella (<td ...>)
br=<br(" ")*("/")?(" ")*>                               #Espressione regolare che rappresenta un tag di ritorno a capo (<br/>)
p=<p(" ")*({Attributo})*(" ")*>                         #Espressione regolare che rappresenta un tag di inizio paragrafo (<p ...>)
DELIM=[ \n\r\t\f]                                       #Espressione regolare che rappresenta un carattere delimitatore
%%

<YYINITIAL> {AperturaTabella}
{
    if(yytext().contains("content_table"))
        yybegin(SCRAPING);
}

<SCRAPING>  {AperturaTabella}   { ContaTabelle++; }

<SCRAPING>  {AperturaCella}
{
    if(yytext().contains("content_title_primopiano"))
    {
        TestoTitolo="";
        yybegin(SCRAPINGTITOLO);
    }
    else if(yytext().contains("content_text_news") && !NotiziaLetta)
    {
        TestoNotizia="";
        yybegin(SCRAPINGNOTIZIA);
    }
}

<SCRAPING> {AperturaSpan}
{
    if(yytext().contains("content_text_news")  && !NotiziaLetta)
        yybegin(SCRAPINGNOTIZIA);
}

<SCRAPING>   "</table>"
{
    if(ContaTabelle==-1)
        yybegin(YYINITIAL);
    else
        ContaTabelle--;
}

<SCRAPINGTITOLO,SCRAPINGNOTIZIA>    {AperturaCella} { ContaCelle++; }

<SCRAPINGTITOLO>  "</td>"
{
    if(ContaCelle==0)
    {
        TestoTitolo=SostituisciCaratteriSpeciali(TestoTitolo);
        yybegin(SCRAPING);
        return new Symbol(sym.TITOLO,TestoTitolo.trim());
    }
    else
        ContaCelle--;
}

<SCRAPINGTITOLO>    .   { TestoTitolo+=yytext(); }

<SCRAPINGNOTIZIA>   {AperturaSpan}  { ContaSpan++; }

<SCRAPINGNOTIZIA>  "</span>"
{
    if(ContaSpan==0)
    {
        TestoNotizia=SostituisciCaratteriSpeciali(TestoNotizia);
        yybegin(SCRAPING);
        NotiziaLetta=true;
        return new Symbol(sym.TESTO,TestoNotizia.trim().replaceFirst(" {72,}",""));
    }
    else
        ContaSpan--;
}

<SCRAPINGNOTIZIA>  "</td>"
{
    if(ContaCelle==0)
    {
        TestoNotizia=SostituisciCaratteriSpeciali(TestoNotizia);
        yybegin(SCRAPING);
        NotiziaLetta=true;
        return new Symbol(sym.TESTO,TestoNotizia.trim().replaceFirst(" {72,}",""));
    }
    else
        ContaCelle--;
}

<SCRAPINGNOTIZIA>  {Link}
{
    TestoLink="";
    yybegin(SCRAPINGLINKNOTIZIA);
}

<SCRAPINGNOTIZIA>  {br}|{p}  { TestoNotizia+=System.getProperty("line.separator"); }

<SCRAPINGNOTIZIA> . { TestoNotizia+=yytext(); }

<SCRAPINGLINKNOTIZIA>   "</a>"
{
    TestoNotizia+=TestoLink;
    yybegin(SCRAPINGNOTIZIA);
}

<SCRAPINGLINKNOTIZIA>   {Tag}   { }

<SCRAPINGLINKNOTIZIA>  .   { TestoLink+=yytext(); }

{DELIM}|.|{Tag} { }
