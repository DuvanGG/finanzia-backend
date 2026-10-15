package com.finanzia.application.port.out;

import com.finanzia.domain.token.DecodedAccessToken;

/**
 * Validación y decodificación de access tokens (outbound port). Rechaza tokens
 * malformados, con firma inválida o expirados (con tolerancia de reloj).
 */
public interface TokenDecoder {

    DecodedAccessToken decode(String accessToken);
}