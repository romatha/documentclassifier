#!/bin/bash
for i in `ls -1 *.txt`;
do cat $i | grep "INFO: Errore" | awk '{print $6"\t"$9}' | sed 's/K-NN=//' | tr . , > $i.Grafico.csv;
mv $i.Grafico.csv `echo $i.Grafico.csv | sed s/.txt//`;
done
