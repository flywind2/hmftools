/*
 * This file is generated by jOOQ.
*/
package org.ensembl.database.homo_sapiens_core.tables;


import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.ensembl.database.homo_sapiens_core.HomoSapiensCore_89_37;
import org.ensembl.database.homo_sapiens_core.Keys;
import org.ensembl.database.homo_sapiens_core.tables.records.DnaAlignFeatureRecord;
import org.jooq.Field;
import org.jooq.Identity;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;
import org.jooq.types.UInteger;
import org.jooq.types.UShort;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.9.5"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DnaAlignFeature extends TableImpl<DnaAlignFeatureRecord> {

    private static final long serialVersionUID = 1953564938;

    /**
     * The reference instance of <code>homo_sapiens_core_89_37.dna_align_feature</code>
     */
    public static final DnaAlignFeature DNA_ALIGN_FEATURE = new DnaAlignFeature();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DnaAlignFeatureRecord> getRecordType() {
        return DnaAlignFeatureRecord.class;
    }

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.dna_align_feature_id</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UInteger> DNA_ALIGN_FEATURE_ID = createField("dna_align_feature_id", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.seq_region_id</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UInteger> SEQ_REGION_ID = createField("seq_region_id", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.seq_region_start</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UInteger> SEQ_REGION_START = createField("seq_region_start", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.seq_region_end</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UInteger> SEQ_REGION_END = createField("seq_region_end", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.seq_region_strand</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Byte> SEQ_REGION_STRAND = createField("seq_region_strand", org.jooq.impl.SQLDataType.TINYINT.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.hit_start</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Integer> HIT_START = createField("hit_start", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.hit_end</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Integer> HIT_END = createField("hit_end", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.hit_strand</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Byte> HIT_STRAND = createField("hit_strand", org.jooq.impl.SQLDataType.TINYINT.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.hit_name</code>.
     */
    public final TableField<DnaAlignFeatureRecord, String> HIT_NAME = createField("hit_name", org.jooq.impl.SQLDataType.VARCHAR.length(40).nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.analysis_id</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UShort> ANALYSIS_ID = createField("analysis_id", org.jooq.impl.SQLDataType.SMALLINTUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.score</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Double> SCORE = createField("score", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.evalue</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Double> EVALUE = createField("evalue", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.perc_ident</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Double> PERC_IDENT = createField("perc_ident", org.jooq.impl.SQLDataType.FLOAT, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.cigar_line</code>.
     */
    public final TableField<DnaAlignFeatureRecord, String> CIGAR_LINE = createField("cigar_line", org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.external_db_id</code>.
     */
    public final TableField<DnaAlignFeatureRecord, UInteger> EXTERNAL_DB_ID = createField("external_db_id", org.jooq.impl.SQLDataType.INTEGERUNSIGNED, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.hcoverage</code>.
     */
    public final TableField<DnaAlignFeatureRecord, Double> HCOVERAGE = createField("hcoverage", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.dna_align_feature.external_data</code>.
     */
    public final TableField<DnaAlignFeatureRecord, String> EXTERNAL_DATA = createField("external_data", org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * Create a <code>homo_sapiens_core_89_37.dna_align_feature</code> table reference
     */
    public DnaAlignFeature() {
        this("dna_align_feature", null);
    }

    /**
     * Create an aliased <code>homo_sapiens_core_89_37.dna_align_feature</code> table reference
     */
    public DnaAlignFeature(String alias) {
        this(alias, DNA_ALIGN_FEATURE);
    }

    private DnaAlignFeature(String alias, Table<DnaAlignFeatureRecord> aliased) {
        this(alias, aliased, null);
    }

    private DnaAlignFeature(String alias, Table<DnaAlignFeatureRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return HomoSapiensCore_89_37.HOMO_SAPIENS_CORE_89_37;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<DnaAlignFeatureRecord, UInteger> getIdentity() {
        return Keys.IDENTITY_DNA_ALIGN_FEATURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<DnaAlignFeatureRecord> getPrimaryKey() {
        return Keys.KEY_DNA_ALIGN_FEATURE_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<DnaAlignFeatureRecord>> getKeys() {
        return Arrays.<UniqueKey<DnaAlignFeatureRecord>>asList(Keys.KEY_DNA_ALIGN_FEATURE_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DnaAlignFeature as(String alias) {
        return new DnaAlignFeature(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public DnaAlignFeature rename(String name) {
        return new DnaAlignFeature(name, null);
    }
}
