package com.aquariux.technical.assessment.trade.mapper;

import com.aquariux.technical.assessment.trade.entity.CryptoPair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CryptoPairMapper {
    
    @Select("""
            SELECT id FROM crypto_pairs WHERE pair_name = #{pairName}
            """)
    Long findIdByPairName(String pairName);

    @Select("""
            SELECT cp.id,
                   cp.base_symbol_id AS baseSymbolId,
                   cp.quote_symbol_id AS quoteSymbolId,
                   cp.pair_name AS pairName,
                   cp.active,
                   base.symbol AS baseSymbol,
                   quote.symbol AS quoteSymbol
            FROM crypto_pairs cp
            INNER JOIN symbols base ON base.id = cp.base_symbol_id
            INNER JOIN symbols quote ON quote.id = cp.quote_symbol_id
            WHERE cp.pair_name = #{pairName}
              AND cp.active = TRUE
              AND base.active = TRUE
              AND quote.active = TRUE
            """)
    CryptoPair findActiveByPairName(String pairName);
}
