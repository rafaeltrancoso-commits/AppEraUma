package com.rrsistemas.erauma.config;

/**
 * Resolve o IP "real" do cliente quando a aplicacao roda atras de um unico proxy
 * reverso confiavel (a borda do Railway) e nao e alcancavel publicamente sem
 * passar por ele.
 *
 * <p>Convencao do cabecalho X-Forwarded-For: cada salto ANEXA ao final o IP de
 * quem lhe enviou a requisicao. Se o cliente original tentar forjar o header
 * (ex.: "X-Forwarded-For: 1.2.3.4"), o proxy confiavel recebe essa requisicao e
 * anexa o IP real que observou, resultando em "1.2.3.4, &lt;ip-real&gt;". Por isso
 * so confiamos no ULTIMO valor da lista - nunca no primeiro, que e escrito
 * livremente pelo cliente e permitiria burlar o limite de tentativas trocando
 * esse valor a cada requisicao.</p>
 *
 * <p><strong>Importante:</strong> esta classe implementa apenas a regra local de
 * parsing acima. Os testes ({@code ClientIpResolverTest}) comprovam somente essa
 * regra (pegar o ultimo valor da lista) - eles NAO comprovam que o proxy real da
 * Railway de fato anexa o IP dessa forma em producao. Essa premissa sobre o
 * comportamento do proxy da Railway ainda precisa ser validada em producao antes
 * de tratar o IP resolvido aqui como confiavel para qualquer decisao alem de
 * rate limiting best-effort.</p>
 */
public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(String forwardedForHeader, String remoteAddr) {
        if (forwardedForHeader != null && !forwardedForHeader.isBlank()) {
            String[] parts = forwardedForHeader.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isBlank()) {
                return last;
            }
        }
        return remoteAddr;
    }
}
