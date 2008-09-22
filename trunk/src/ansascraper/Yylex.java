package ansascraper;
import java_cup.runtime.*;


public class Yylex implements java_cup.runtime.Scanner {
	private final int YY_BUFFER_SIZE = 512;
	private final int YY_F = -1;
	private final int YY_NO_STATE = -1;
	private final int YY_NOT_ACCEPT = 0;
	private final int YY_START = 1;
	private final int YY_END = 2;
	private final int YY_NO_ANCHOR = 4;
	private final int YY_BOL = 65536;
	private final int YY_EOF = 65537;

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
	private java.io.BufferedReader yy_reader;
	private int yy_buffer_index;
	private int yy_buffer_read;
	private int yy_buffer_start;
	private int yy_buffer_end;
	private char yy_buffer[];
	private boolean yy_at_bol;
	private int yy_lexical_state;

	public Yylex (java.io.Reader reader) {
		this ();
		if (null == reader) {
			throw (new Error("Error: Bad input stream initializer."));
		}
		yy_reader = new java.io.BufferedReader(reader);
	}

	public Yylex (java.io.InputStream instream) {
		this ();
		if (null == instream) {
			throw (new Error("Error: Bad input stream initializer."));
		}
		yy_reader = new java.io.BufferedReader(new java.io.InputStreamReader(instream));
	}

	private Yylex () {
		yy_buffer = new char[YY_BUFFER_SIZE];
		yy_buffer_read = 0;
		yy_buffer_index = 0;
		yy_buffer_start = 0;
		yy_buffer_end = 0;
		yy_at_bol = true;
		yy_lexical_state = YYINITIAL;
	}

	private boolean yy_eof_done = false;
	private final int SCRAPINGNOTIZIA = 3;
	private final int YYINITIAL = 0;
	private final int SCRAPING = 1;
	private final int SCRAPINGLINKNOTIZIA = 4;
	private final int SCRAPINGTITOLO = 2;
	private final int yy_state_dtrans[] = {
		0,
		107,
		55,
		65,
		89
	};
	private void yybegin (int state) {
		yy_lexical_state = state;
	}
	private int yy_advance ()
		throws java.io.IOException {
		int next_read;
		int i;
		int j;

		if (yy_buffer_index < yy_buffer_read) {
			return yy_buffer[yy_buffer_index++];
		}

		if (0 != yy_buffer_start) {
			i = yy_buffer_start;
			j = 0;
			while (i < yy_buffer_read) {
				yy_buffer[j] = yy_buffer[i];
				++i;
				++j;
			}
			yy_buffer_end = yy_buffer_end - yy_buffer_start;
			yy_buffer_start = 0;
			yy_buffer_read = j;
			yy_buffer_index = j;
			next_read = yy_reader.read(yy_buffer,
					yy_buffer_read,
					yy_buffer.length - yy_buffer_read);
			if (-1 == next_read) {
				return YY_EOF;
			}
			yy_buffer_read = yy_buffer_read + next_read;
		}

		while (yy_buffer_index >= yy_buffer_read) {
			if (yy_buffer_index >= yy_buffer.length) {
				yy_buffer = yy_double(yy_buffer);
			}
			next_read = yy_reader.read(yy_buffer,
					yy_buffer_read,
					yy_buffer.length - yy_buffer_read);
			if (-1 == next_read) {
				return YY_EOF;
			}
			yy_buffer_read = yy_buffer_read + next_read;
		}
		return yy_buffer[yy_buffer_index++];
	}
	private void yy_move_end () {
		if (yy_buffer_end > yy_buffer_start &&
		    '\n' == yy_buffer[yy_buffer_end-1])
			yy_buffer_end--;
		if (yy_buffer_end > yy_buffer_start &&
		    '\r' == yy_buffer[yy_buffer_end-1])
			yy_buffer_end--;
	}
	private boolean yy_last_was_cr=false;
	private void yy_mark_start () {
		yy_buffer_start = yy_buffer_index;
	}
	private void yy_mark_end () {
		yy_buffer_end = yy_buffer_index;
	}
	private void yy_to_mark () {
		yy_buffer_index = yy_buffer_end;
		yy_at_bol = (yy_buffer_end > yy_buffer_start) &&
		            ('\r' == yy_buffer[yy_buffer_end-1] ||
		             '\n' == yy_buffer[yy_buffer_end-1] ||
		             2028/*LS*/ == yy_buffer[yy_buffer_end-1] ||
		             2029/*PS*/ == yy_buffer[yy_buffer_end-1]);
	}
	private java.lang.String yytext () {
		return (new java.lang.String(yy_buffer,
			yy_buffer_start,
			yy_buffer_end - yy_buffer_start));
	}
	private int yylength () {
		return yy_buffer_end - yy_buffer_start;
	}
	private char[] yy_double (char buf[]) {
		int i;
		char newbuf[];
		newbuf = new char[2*buf.length];
		for (i = 0; i < buf.length; ++i) {
			newbuf[i] = buf[i];
		}
		return newbuf;
	}
	private final int YY_E_INTERNAL = 0;
	private final int YY_E_MATCH = 1;
	private java.lang.String yy_error_string[] = {
		"Error: Internal error.\n",
		"Error: Unmatched input.\n"
	};
	private void yy_error (int code,boolean fatal) {
		java.lang.System.out.print(yy_error_string[code]);
		java.lang.System.out.flush();
		if (fatal) {
			throw new Error("Fatal Error.\n");
		}
	}
	private int[][] unpackFromString(int size1, int size2, String st) {
		int colonIndex = -1;
		String lengthString;
		int sequenceLength = 0;
		int sequenceInteger = 0;

		int commaIndex;
		String workString;

		int res[][] = new int[size1][size2];
		for (int i= 0; i < size1; i++) {
			for (int j= 0; j < size2; j++) {
				if (sequenceLength != 0) {
					res[i][j] = sequenceInteger;
					sequenceLength--;
					continue;
				}
				commaIndex = st.indexOf(',');
				workString = (commaIndex==-1) ? st :
					st.substring(0, commaIndex);
				st = st.substring(commaIndex+1);
				colonIndex = workString.indexOf(':');
				if (colonIndex == -1) {
					res[i][j]=Integer.parseInt(workString);
					continue;
				}
				lengthString =
					workString.substring(colonIndex+1);
				sequenceLength=Integer.parseInt(lengthString);
				workString=workString.substring(0,colonIndex);
				sequenceInteger=Integer.parseInt(workString);
				res[i][j] = sequenceInteger;
				sequenceLength--;
			}
		}
		return res;
	}
	private int yy_acpt[] = {
		/* 0 */ YY_NOT_ACCEPT,
		/* 1 */ YY_NO_ANCHOR,
		/* 2 */ YY_NO_ANCHOR,
		/* 3 */ YY_NO_ANCHOR,
		/* 4 */ YY_NO_ANCHOR,
		/* 5 */ YY_NO_ANCHOR,
		/* 6 */ YY_NO_ANCHOR,
		/* 7 */ YY_NO_ANCHOR,
		/* 8 */ YY_NO_ANCHOR,
		/* 9 */ YY_NO_ANCHOR,
		/* 10 */ YY_NO_ANCHOR,
		/* 11 */ YY_NO_ANCHOR,
		/* 12 */ YY_NO_ANCHOR,
		/* 13 */ YY_NO_ANCHOR,
		/* 14 */ YY_NO_ANCHOR,
		/* 15 */ YY_NO_ANCHOR,
		/* 16 */ YY_NO_ANCHOR,
		/* 17 */ YY_NO_ANCHOR,
		/* 18 */ YY_NO_ANCHOR,
		/* 19 */ YY_NO_ANCHOR,
		/* 20 */ YY_NOT_ACCEPT,
		/* 21 */ YY_NO_ANCHOR,
		/* 22 */ YY_NO_ANCHOR,
		/* 23 */ YY_NO_ANCHOR,
		/* 24 */ YY_NO_ANCHOR,
		/* 25 */ YY_NOT_ACCEPT,
		/* 26 */ YY_NOT_ACCEPT,
		/* 27 */ YY_NOT_ACCEPT,
		/* 28 */ YY_NOT_ACCEPT,
		/* 29 */ YY_NOT_ACCEPT,
		/* 30 */ YY_NOT_ACCEPT,
		/* 31 */ YY_NOT_ACCEPT,
		/* 32 */ YY_NOT_ACCEPT,
		/* 33 */ YY_NOT_ACCEPT,
		/* 34 */ YY_NOT_ACCEPT,
		/* 35 */ YY_NOT_ACCEPT,
		/* 36 */ YY_NOT_ACCEPT,
		/* 37 */ YY_NOT_ACCEPT,
		/* 38 */ YY_NOT_ACCEPT,
		/* 39 */ YY_NOT_ACCEPT,
		/* 40 */ YY_NOT_ACCEPT,
		/* 41 */ YY_NOT_ACCEPT,
		/* 42 */ YY_NOT_ACCEPT,
		/* 43 */ YY_NOT_ACCEPT,
		/* 44 */ YY_NOT_ACCEPT,
		/* 45 */ YY_NOT_ACCEPT,
		/* 46 */ YY_NOT_ACCEPT,
		/* 47 */ YY_NOT_ACCEPT,
		/* 48 */ YY_NOT_ACCEPT,
		/* 49 */ YY_NOT_ACCEPT,
		/* 50 */ YY_NOT_ACCEPT,
		/* 51 */ YY_NOT_ACCEPT,
		/* 52 */ YY_NOT_ACCEPT,
		/* 53 */ YY_NOT_ACCEPT,
		/* 54 */ YY_NOT_ACCEPT,
		/* 55 */ YY_NOT_ACCEPT,
		/* 56 */ YY_NOT_ACCEPT,
		/* 57 */ YY_NOT_ACCEPT,
		/* 58 */ YY_NOT_ACCEPT,
		/* 59 */ YY_NOT_ACCEPT,
		/* 60 */ YY_NOT_ACCEPT,
		/* 61 */ YY_NOT_ACCEPT,
		/* 62 */ YY_NOT_ACCEPT,
		/* 63 */ YY_NOT_ACCEPT,
		/* 64 */ YY_NOT_ACCEPT,
		/* 65 */ YY_NOT_ACCEPT,
		/* 66 */ YY_NOT_ACCEPT,
		/* 67 */ YY_NOT_ACCEPT,
		/* 68 */ YY_NOT_ACCEPT,
		/* 69 */ YY_NOT_ACCEPT,
		/* 70 */ YY_NOT_ACCEPT,
		/* 71 */ YY_NOT_ACCEPT,
		/* 72 */ YY_NOT_ACCEPT,
		/* 73 */ YY_NOT_ACCEPT,
		/* 74 */ YY_NOT_ACCEPT,
		/* 75 */ YY_NOT_ACCEPT,
		/* 76 */ YY_NOT_ACCEPT,
		/* 77 */ YY_NOT_ACCEPT,
		/* 78 */ YY_NOT_ACCEPT,
		/* 79 */ YY_NOT_ACCEPT,
		/* 80 */ YY_NOT_ACCEPT,
		/* 81 */ YY_NOT_ACCEPT,
		/* 82 */ YY_NOT_ACCEPT,
		/* 83 */ YY_NOT_ACCEPT,
		/* 84 */ YY_NOT_ACCEPT,
		/* 85 */ YY_NOT_ACCEPT,
		/* 86 */ YY_NOT_ACCEPT,
		/* 87 */ YY_NOT_ACCEPT,
		/* 88 */ YY_NOT_ACCEPT,
		/* 89 */ YY_NOT_ACCEPT,
		/* 90 */ YY_NOT_ACCEPT,
		/* 91 */ YY_NOT_ACCEPT,
		/* 92 */ YY_NOT_ACCEPT,
		/* 93 */ YY_NOT_ACCEPT,
		/* 94 */ YY_NOT_ACCEPT,
		/* 95 */ YY_NOT_ACCEPT,
		/* 96 */ YY_NOT_ACCEPT,
		/* 97 */ YY_NOT_ACCEPT,
		/* 98 */ YY_NO_ANCHOR,
		/* 99 */ YY_NOT_ACCEPT,
		/* 100 */ YY_NOT_ACCEPT,
		/* 101 */ YY_NOT_ACCEPT,
		/* 102 */ YY_NOT_ACCEPT,
		/* 103 */ YY_NOT_ACCEPT,
		/* 104 */ YY_NOT_ACCEPT,
		/* 105 */ YY_NOT_ACCEPT,
		/* 106 */ YY_NOT_ACCEPT,
		/* 107 */ YY_NOT_ACCEPT,
		/* 108 */ YY_NOT_ACCEPT,
		/* 109 */ YY_NOT_ACCEPT,
		/* 110 */ YY_NOT_ACCEPT,
		/* 111 */ YY_NOT_ACCEPT,
		/* 112 */ YY_NOT_ACCEPT,
		/* 113 */ YY_NOT_ACCEPT,
		/* 114 */ YY_NOT_ACCEPT,
		/* 115 */ YY_NOT_ACCEPT,
		/* 116 */ YY_NOT_ACCEPT,
		/* 117 */ YY_NOT_ACCEPT,
		/* 118 */ YY_NOT_ACCEPT,
		/* 119 */ YY_NOT_ACCEPT,
		/* 120 */ YY_NOT_ACCEPT
	};
	private int yy_cmap[] = unpackFromString(1,65538,
"18:10,20,18:2,20,18:18,7,11:3,18:2,11:4,18:3,9,11,17,9:10,11:2,1,10,12,11:2" +
",3,4,8,13,6,8:6,5,8,16,8,15,8,19,14,2,8:6,11:3,18,11,18,3,4,8,13,6,8:6,5,8," +
"16,8,15,8,19,14,2,8:6,18:65413,0:2")[0];

	private int yy_rmap[] = unpackFromString(1,121,
"0,1,2,1:5,3,1:2,4,1:5,5,1:2,6,1:4,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21" +
",22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46" +
",47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71" +
",72,73,74,75,76,77,78,79,80,81,34,61,82,83,84,85,86,87,88,89,90,91,92,93,94" +
",95,96,97,98,99,100")[0];

	private int yy_nxt[][] = unpackFromString(101,21,
"1,2,21:19,-1:23,20,120:4,-1,120,-1:4,120:4,25,-1,120,-1:3,56,120:4,-1,120,-" +
"1:4,120:4,57,-1,120,-1:3,56,66,67,120:2,-1,120,-1:4,120,116,68,120,69,-1,12" +
"0,-1:3,90:5,-1,90,-1:4,90:4,91,-1,90,-1:3,26,115,26:3,27,26,120,-1:2,21,26:" +
"4,28,-1,26,-1:3,120:5,-1,120,-1:4,120:4,-1:2,120,-1:3,26:5,27,26:2,29,-1,21" +
",26:4,28,-1,26,-1:3,30:5,27,30,-1:3,21,30:4,28,-1,30,-1:13,21,-1:10,29:10,2" +
"1,29:5,-1,29,-1:3,30:5,-1,30:2,29,-1:2,30:4,-1:2,30,-1:3,32:5,33,32,26,29,-" +
"1,3,32:4,28,-1,32,-1:3,32:5,27,32:2,34,-1,21,32:4,28,-1,32,-1:3,35:5,33,35," +
"-1:3,3,35:4,28,-1,35,-1:3,34:10,3,34:5,-1,34,-1:3,35:5,-1,35:2,34,-1:2,35:4" +
",-1:2,35,-1:3,26,117,26:3,27,26,120,-1:2,21,39,26:3,28,-1,26,-1:3,26:5,27,2" +
"6,120,-1:2,21,26:2,110,26,28,-1,26,-1:3,109,120:4,-1,120,-1:4,120:4,-1:2,12" +
"0,-1:3,40:5,41,40,26,29,-1,4,40:4,28,-1,40,-1:3,40:5,27,40:2,42,-1,21,40:4," +
"28,-1,40,-1:3,43:5,41,43,-1:3,4,43:4,28,-1,43,-1:3,42:10,4,42:5,-1,42,-1:3," +
"43:5,-1,43:2,42,-1:2,43:4,-1:2,43,-1:3,46:5,47,46,26,29,-1,5,46:4,28,-1,46," +
"-1:3,100:5,48,100,26,29,-1,6,100:4,28,-1,100,-1:3,46:5,27,46:2,49,-1,21,46:" +
"4,28,-1,46,-1:3,50:5,47,50,-1:3,5,50:4,28,-1,50,-1:3,54:5,48,54,-1:3,6,54:4" +
",28,-1,54,-1:3,49:10,5,49:5,-1,49,-1:3,50:5,-1,50:2,49,-1:2,50:4,-1:2,50,-1" +
":3,26:5,27,26:2,29,-1,7,26:4,28,-1,26,-1:3,52:5,27,52:2,53,-1,21,52:4,28,-1" +
",52,-1:3,53:10,6,53:5,-1,53,-1:3,54:5,-1,54:2,53,-1:2,54:4,-1:2,54,-1,1,8,2" +
"2:18,21,-1:2,26:5,27,26,120,-1:2,21,58,26:3,28,-1,26,-1:3,59,120:4,-1,120,-" +
"1:4,120:4,-1:2,120,-1:3,60:5,61,60,26,29,-1,9,60:4,28,-1,60,-1:3,26:5,27,26" +
",120,-1:2,21,62,26:3,28,-1,26,-1:3,60:5,27,60:2,63,-1,21,60:4,28,-1,60,-1:3" +
",64:5,61,64,-1:3,9,64:4,28,-1,64,-1:3,26:5,27,26:2,29,-1,10,26:4,28,-1,26,-" +
"1:3,63:10,9,63:5,-1,63,-1:3,64:5,-1,64:2,63,-1:2,64:4,-1:2,64,-1,1,11,23:18" +
",21,-1:2,70:5,71,70,120,-1:2,12,70:4,28,-1,70,-1:3,26:5,27,26,120,-1:2,21,2" +
"6:4,28,-1,72,-1:3,101:5,73,101,120,-1:2,13,101:4,28,-1,101,-1:3,74,120:4,-1" +
",120,-1:4,120,118,120:2,-1:2,120,-1:3,70:5,27,70:2,75,-1,21,70:4,28,-1,70,-" +
"1:3,76:5,71,76,-1:3,12,76:4,28,-1,76,-1:3,26:5,77,26:2,29,-1,13,26:4,78,-1," +
"26,-1:3,81:5,73,81,-1:3,13,81:4,28,-1,81,-1:3,26:5,27,26,120,-1:2,21,82,26:" +
"3,28,-1,26,-1:3,75:10,12,75:5,-1,75,-1:3,76:5,-1,76:2,75,-1:2,76:4,-1:2,76," +
"-1:3,30:5,77,30,-1:3,13,30:4,78,-1,30,-1:8,78,-1:4,13,-1:10,79:5,27,79:2,80" +
",-1,21,79:4,28,-1,79,-1:3,80:10,13,80:5,-1,80,-1:3,81:5,-1,81:2,80,-1:2,81:" +
"4,-1:2,81,-1:3,26:5,27,26:2,29,-1,14,26:4,28,-1,26,-1:3,84:5,85,84,26,29,-1" +
",15,84:4,28,-1,84,-1:3,84:5,27,84:2,87,-1,21,84:4,28,-1,84,-1:3,88:5,85,88," +
"-1:3,15,88:4,28,-1,88,-1:3,26:5,27,26:2,29,-1,16,26:4,28,-1,26,-1:3,87:10,1" +
"5,87:5,-1,87,-1:3,88:5,-1,88:2,87,-1:2,88:4,-1:2,88,-1,1,17,24:18,21,-1:2,9" +
"2:5,93,92,90,-1:2,18,92:4,94,-1,92,-1:3,90,95,90:3,-1,90,-1:4,90:4,-1:2,90," +
"-1:3,92:5,93,92:2,96,-1,18,92:4,94,-1,92,-1:3,97:5,93,97,-1:3,18,97:4,94,-1" +
",97,-1:13,18,-1:10,92:5,93,92,90,-1:2,19,92:4,94,-1,92,-1:3,96:10,18,96:5,-" +
"1,96,-1:3,97:5,-1,97:2,96,-1:2,97:4,-1:2,97,-1:3,36,120:4,-1,120,-1:4,120,3" +
"7,120:2,38,-1,120,-1:3,26:4,31,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:5,27," +
"26:2,29,-1,21,26:3,44,28,-1,26,-1:3,26:4,45,27,26:2,29,-1,21,26:4,28,-1,26," +
"-1:3,26:4,51,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:5,27,26:2,29,-1,21,26:3" +
",83,28,-1,26,-1:3,26:5,27,26:2,29,-1,21,26:3,86,28,-1,26,-1,1,98,21:19,-1:2" +
",26:3,99,26,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26,119,26:3,27,26,120,-1:2," +
"21,26:4,28,-1,26,-1:3,26,102,26:3,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:3," +
"103,26,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:3,104,26,27,26:2,29,-1,21,26:" +
"4,28,-1,26,-1:3,26,105,26:3,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26,106,26:3" +
",27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:2,108,26:2,27,26:2,29,-1,21,26:4,28" +
",-1,26,-1:3,26:5,27,26,120,-1:2,21,26:2,113,26,28,-1,26,-1:3,26:2,111,26:2," +
"27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:5,27,26,120,-1:2,21,26:2,114,26,28,-" +
"1,26,-1:3,26:2,112,26:2,27,26:2,29,-1,21,26:4,28,-1,26,-1:3,26:5,27,26,120," +
"-1:2,21,26:4,28,-1,26,-1");

	public java_cup.runtime.Symbol next_token ()
		throws java.io.IOException {
		int yy_lookahead;
		int yy_anchor = YY_NO_ANCHOR;
		int yy_state = yy_state_dtrans[yy_lexical_state];
		int yy_next_state = YY_NO_STATE;
		int yy_last_accept_state = YY_NO_STATE;
		boolean yy_initial = true;
		int yy_this_accept;

		yy_mark_start();
		yy_this_accept = yy_acpt[yy_state];
		if (YY_NOT_ACCEPT != yy_this_accept) {
			yy_last_accept_state = yy_state;
			yy_mark_end();
		}
		while (true) {
			if (yy_initial && yy_at_bol) yy_lookahead = YY_BOL;
			else yy_lookahead = yy_advance();
			yy_next_state = YY_F;
			yy_next_state = yy_nxt[yy_rmap[yy_state]][yy_cmap[yy_lookahead]];
			if (YY_EOF == yy_lookahead && true == yy_initial) {

    return new Symbol(sym.EOF);
			}
			if (YY_F != yy_next_state) {
				yy_state = yy_next_state;
				yy_initial = false;
				yy_this_accept = yy_acpt[yy_state];
				if (YY_NOT_ACCEPT != yy_this_accept) {
					yy_last_accept_state = yy_state;
					yy_mark_end();
				}
			}
			else {
				if (YY_NO_STATE == yy_last_accept_state) {
					throw (new Error("Lexical Error: Unmatched Input."));
				}
				else {
					yy_anchor = yy_acpt[yy_last_accept_state];
					if (0 != (YY_END & yy_anchor)) {
						yy_move_end();
					}
					yy_to_mark();
					switch (yy_last_accept_state) {
					case 1:
						
					case -2:
						break;
					case 2:
						{ }
					case -3:
						break;
					case 3:
						{
    if(yytext().contains("content_table"))
        yybegin(SCRAPING);
}
					case -4:
						break;
					case 4:
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
					case -5:
						break;
					case 5:
						{
    if(yytext().contains("content_text_news")  && !NotiziaLetta)
        yybegin(SCRAPINGNOTIZIA);
}
					case -6:
						break;
					case 6:
						{ ContaTabelle++; }
					case -7:
						break;
					case 7:
						{
    if(ContaTabelle==-1)
        yybegin(YYINITIAL);
    else
        ContaTabelle--;
}
					case -8:
						break;
					case 8:
						{ TestoTitolo+=yytext(); }
					case -9:
						break;
					case 9:
						{ ContaCelle++; }
					case -10:
						break;
					case 10:
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
					case -11:
						break;
					case 11:
						{ TestoNotizia+=yytext(); }
					case -12:
						break;
					case 12:
						{
    TestoLink="";
    yybegin(SCRAPINGLINKNOTIZIA);
}
					case -13:
						break;
					case 13:
						{ TestoNotizia+=System.getProperty("line.separator"); }
					case -14:
						break;
					case 14:
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
					case -15:
						break;
					case 15:
						{ ContaSpan++; }
					case -16:
						break;
					case 16:
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
					case -17:
						break;
					case 17:
						{ TestoLink+=yytext(); }
					case -18:
						break;
					case 18:
						{ }
					case -19:
						break;
					case 19:
						{
    TestoNotizia+=TestoLink;
    yybegin(SCRAPINGNOTIZIA);
}
					case -20:
						break;
					case 21:
						{ }
					case -21:
						break;
					case 22:
						{ TestoTitolo+=yytext(); }
					case -22:
						break;
					case 23:
						{ TestoNotizia+=yytext(); }
					case -23:
						break;
					case 24:
						{ TestoLink+=yytext(); }
					case -24:
						break;
					case 98:
						{ }
					case -25:
						break;
					default:
						yy_error(YY_E_INTERNAL,false);
					case -1:
					}
					yy_initial = true;
					yy_state = yy_state_dtrans[yy_lexical_state];
					yy_next_state = YY_NO_STATE;
					yy_last_accept_state = YY_NO_STATE;
					yy_mark_start();
					yy_this_accept = yy_acpt[yy_state];
					if (YY_NOT_ACCEPT != yy_this_accept) {
						yy_last_accept_state = yy_state;
						yy_mark_end();
					}
				}
			}
		}
	}
}
