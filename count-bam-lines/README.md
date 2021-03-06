# COBALT

**Co**unt **ba**m **l**ines determines the read depth ratios of the supplied tumor and reference genomes. 

COBALT starts with the raw read counts per 1,000 base window for both normal and tumor samples by counting the number of alignment starts 
in the respective bam files with a mapping quality score of at least 10 that is neither unmapped, duplicated, secondary, nor supplementary. 
Windows with a GC content less than 0.2 or greater than 0.6 or with an average mappability below 0.85 are excluded from further analysis.

Next we apply a GC normalization to calculate the read ratios. 
We divide the read count of each window by the median read count of all windows sharing the same GC content then normalise further to the 
ratio of the median to mean read count of all windows. 

The reference sample ratios have a further ‘diploid’ normalization applied to them to remove megabase scale GC biases. 
This normalization assumes that the median ratio of each 10Mb window (minimum 1Mb readable) should be diploid for autosomes and haploid for 
sex chromosomes in males in the germline sample.

Finally, the Bioconductor copy number package is used to generate segments from the ratio file.

## Installation

To install, download the latest compiled jar file from the [download links](#version-history-and-download-links) and the appropriate GC profile from [HMFTools-Resources > Cobalt](https://resources.hartwigmedicalfoundation.nl/).

COBALT depends on the Bioconductor [copynumber](http://bioconductor.org/packages/release/bioc/html/copynumber.html) package for segmentation.
After installing [R](https://www.r-project.org/) or [RStudio](https://rstudio.com/), the copy number package can be added with the following R commands:
```
    library(BiocManager)
    install("copynumber")
```

COBALT requires Java 1.8+ and can be run with the minimum set of arguments as follows:

```
java -cp -Xmx8G cobalt.jar com.hartwig.hmftools.cobalt.CountBamLinesApplication \
    -reference COLO829R -reference_bam /run_dir/COLO829R.bam \ 
    -tumor COLO829T -tumor_bam /run_dir/COLO829T.bam \ 
    -output_dir /run_dir/cobalt \ 
    -threads 16 \ 
    -gc_profile /path/to/GC_profile.hg19.1000bp.cnp
```


## Mandatory Arguments

Argument  | Description
---|---
reference | Name of the reference sample
reference_bam | Path to reference BAM file
tumor | Name of tumor sample
tumor_bam | Path to tumor BAM file
output_dir | Path to the output directory. This directory will be created if it does not already exist
gc_profile | Path to GC profile 

The GC Profile file used by HMF (GC_profile.hg19.1000bp.cnp) is available to download from [HMF-Pipeline-Resources](https://resources.hartwigmedicalfoundation.nl). 
A HG38 equivalent is also available.

COBALT supports both BAM and CRAM file formats. If using CRAM, the ref_genome argument must be included.

## Optional Arguments

Argument | Default | Description 
---|---|---
threads | 4 | Number of threads to use
min_quality | 10 | Min quality
ref_genome | None | Path to the reference genome fasta file if using CRAM files
validation_stringency | STRICT | SAM validation strategy: STRICT, SILENT, LENIENT

## Performance Characteristics
Performance numbers were taken from a 72 core machine using COLO829 data with an average read depth of 35 and 93 in the normal and tumor respectively. 
Elapsed time is measured in minutes. 
CPU time is minutes spent in user mode. 
Peak memory is measure in gigabytes.


Threads | Elapsed Time| CPU Time | Peak Mem
---|---|---|---
1 | 111 | 122 | 3.85
8 | 17 | 127 | 4.49
16 | 10 | 139 | 4.58 
32 | 11 | 184 | 4.33
48 | 10 | 153 | 4.35


## Output
The following tab delimited files are written:

`/run_dir/cobalt/TUMOR.chr.len`

`/run_dir/cobalt/TUMOR.cobalt.ratio.tsv`

`/run_dir/cobalt/TUMOR.cobalt.ratio.pcf`

`/run_dir/cobalt/REFERENCE.cobalt.ratio.pcf`

TUMOR.cobalt.ratio.tsv contains the counts and ratios of the reference and tumor:

Chromosome | Position | ReferenceReadCount | TumorReadCount | ReferenceGCRatio | TumorGCRatio | ReferenceGCDiploidRatio
---|---|---|---|---|---|---
1|4000001|204|504|0.8803|0.855|0.8982
1|4001001|203|570|0.8429|0.9149|0.86
1|4002001|155|473|0.6463|0.7654|0.6594
1|4003001|260|566|1.098|0.9328|1.1203
1|4004001|256|550|1.1144|0.9428|1.1371

TUMOR.cobalt.ratio.pcf and REFERENCE.cobalt.ratio.pcf contain the segmented regions determined from the ratios.

## Version History and Download Links
- [1.8](https://github.com/hartwigmedical/hmftools/releases/tag/cobalt-v1.8)
  - Added `validation_stringency` parameter.
  - Added explicit `stringsAsFactors = T` to R script
- [1.7](https://github.com/hartwigmedical/hmftools/releases/tag/cobalt-v1.7)
  - Exit gracefully on exceptions
  - Changed file names and headers for better consistency with other HMF tools
- [1.6](https://github.com/hartwigmedical/hmftools/releases/tag/cobalt-v1.6)
  - CRAM support
- 1.5
  - Support for HG38
- 1.4
  - Support for Klinefelter syndrome