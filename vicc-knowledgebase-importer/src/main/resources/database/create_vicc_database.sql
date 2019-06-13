SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS viccEntry;
CREATE TABLE viccEntry
(   id int NOT NULL AUTO_INCREMENT,
    source varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS gene;
CREATE TABLE gene
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    geneName varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS geneIdentifier;
CREATE TABLE geneIdentifier
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    symbol varchar(255) NOT NULL,
    entrezId varchar(255) NOT NULL,
    ensemblGeneId varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS featureName;
CREATE TABLE featureName
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    featureName varchar(2500) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS tag;
CREATE TABLE tag
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    tagName varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS devTag;
CREATE TABLE devTag
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    devTagName varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS feature;
CREATE TABLE feature
(   id int NOT NULL AUTO_INCREMENT,
    viccEntryId int NOT NULL,
    name varchar(255),
    biomarkerType varchar(255),
    referenceName varchar(255),
    chromosome varchar(255),
    start varchar(255),
    end varchar(255),
    ref varchar(255),
    alt varchar(255),
    provenanceRule varchar(255),
    geneSymbol varchar(255),
    entrezId varchar(255),
    description varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (viccEntryId) REFERENCES viccEntry(id)
);

DROP TABLE IF EXISTS provenance;
CREATE TABLE provenance
(   id int NOT NULL AUTO_INCREMENT,
    featureEntryId int NOT NULL,
    provenance varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (featureEntryId) REFERENCES feature(id)
);

DROP TABLE IF EXISTS synonyms;
CREATE TABLE synonyms
(   id int NOT NULL AUTO_INCREMENT,
    featureEntryId int NOT NULL,
    synonyms varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (featureEntryId) REFERENCES feature(id)
);

DROP TABLE IF EXISTS links;
CREATE TABLE links
(   id int NOT NULL AUTO_INCREMENT,
    featureEntryId int NOT NULL,
    links varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (featureEntryId) REFERENCES feature(id)
);

DROP TABLE IF EXISTS sequenceOntology;
CREATE TABLE sequenceOntology
(   id int NOT NULL AUTO_INCREMENT,
    featureEntryId int NOT NULL,
    soid varchar(255) NOT NULL,
    parentSoid varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    parentName varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (featureEntryId) REFERENCES feature(id)
);

DROP TABLE IF EXISTS hierarchy;
CREATE TABLE hierarchy
(   id int NOT NULL AUTO_INCREMENT,
    sequenceOntologyEntryId int NOT NULL,
    hierarchy varchar(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (sequenceOntologyEntryId) REFERENCES sequenceOntology(id)
);

SET FOREIGN_KEY_CHECKS = 1;