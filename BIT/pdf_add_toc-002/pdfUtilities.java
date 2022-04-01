package com.itextpdf.jumpstart;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.property.AreaBreakType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;

public class pdfUtilities {

    private static final Logger logger = LogManager.getLogger(pdfUtilities.class);

    public static PdfDocument getPdfFromFilepath(String pdfName) throws IOException {
        return new PdfDocument(new PdfReader(pdfName));
    }

    public static void deleteFile(String filePath){
        File file = new File(filePath);
        if(file.delete()){
            logger.info("Successfully deleted" + filePath);
        }
        else{
            logger.warn("Unable to delete" + filePath);
        }
    }

    public static PdfDocument createBlankDocumentWithLength(int length) throws FileNotFoundException {
        PdfDocument pdf = new PdfDocument(new PdfWriter("./input/temp.pdf"));
        Document document = new Document(pdf);

        for(int i=0; i<length; i++){
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        }
        pdf.removePage(1);
        pdf.close();
        return pdf;
    }

    public static PdfDocument rearrangePdf(String pdfSource, int firstPageFromCut, int lastPageFromCut, int insertLocation, String output) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(pdfSource),new PdfWriter(output));
        PdfMerger merger = new PdfMerger(pdf);

        PdfDocument sourcePdf = new PdfDocument(new PdfReader(pdfSource));

        if(firstPageFromCut <= insertLocation && insertLocation <= lastPageFromCut){
            if(firstPageFromCut == 1){
                merger.merge(sourcePdf, lastPageFromCut+1, lastPageFromCut+insertLocation);
                merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                merger.merge(sourcePdf, lastPageFromCut+insertLocation+1, sourcePdf.getNumberOfPages());
            }
            else{
                merger.merge(sourcePdf, 1, firstPageFromCut-1);
                merger.merge(sourcePdf, lastPageFromCut+1, lastPageFromCut+insertLocation-firstPageFromCut);
                merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                merger.merge(sourcePdf, lastPageFromCut+insertLocation-firstPageFromCut+1, sourcePdf.getNumberOfPages());
            }
        }
        else{
            if(firstPageFromCut == 1){
                if(insertLocation == sourcePdf.getNumberOfPages()){
                    merger.merge(sourcePdf, lastPageFromCut+1, insertLocation);
                    merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                }
                else if(insertLocation < sourcePdf.getNumberOfPages()){
                    merger.merge(sourcePdf, lastPageFromCut+1, insertLocation);
                    merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                    merger.merge(sourcePdf, insertLocation+1, sourcePdf.getNumberOfPages());
                }
            }
            else{
                if(lastPageFromCut == sourcePdf.getNumberOfPages()){
                    if(insertLocation == 0){
                        merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                        merger.merge(sourcePdf, insertLocation+1, firstPageFromCut-1);
                    }
                    else{
                        merger.merge(sourcePdf, 1, insertLocation);
                        merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                        merger.merge(sourcePdf, insertLocation+1, firstPageFromCut-1);
                    }
                }
                else{
                    if(insertLocation == 0){
                        merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                        merger.merge(sourcePdf, insertLocation+1, firstPageFromCut-1);
                        merger.merge(sourcePdf, lastPageFromCut+1, sourcePdf.getNumberOfPages());
                    }
                    else if(insertLocation == sourcePdf.getNumberOfPages()){
                        merger.merge(sourcePdf, 1, firstPageFromCut-1);
                        merger.merge(sourcePdf, lastPageFromCut+1, insertLocation);
                        merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                    }
                    else {
                        merger.merge(sourcePdf, 1, firstPageFromCut - 1);
                        merger.merge(sourcePdf, lastPageFromCut + 1, insertLocation);
                        merger.merge(sourcePdf, firstPageFromCut, lastPageFromCut);
                        merger.merge(sourcePdf, insertLocation + 1, sourcePdf.getNumberOfPages());
                    }
                }
            }
        }
        sourcePdf.close();
        pdf.close();
        return pdf;
    }
}