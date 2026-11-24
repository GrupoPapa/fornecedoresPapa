package br.com.grupopapa.cfp.systock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;

/**
 * Cliente da API externa do Systock (somente leitura: lista e detalhe dos pedidos de compra).
 * As rotas PUT da API (gravar id do ERP / cancelar) nao sao usadas pelo portal.
 * Sem token configurado (CFP_SYSTOCK_TOKEN) a integracao fica desligada.
 */
@Component
public class SystockClient {

    private static final Logger log = LoggerFactory.getLogger(SystockClient.class);

    private final RestClient http;
    private final ObjectMapper mapper;
    private final boolean habilitado;

    public SystockClient(RestClient.Builder builder, ObjectMapper mapper,
                         @Value("${app.systock.url:http://192.168.254.245:8000/api/externa}") String url,
                         @Value("${app.systock.token:}") String token,
                         @Value("${app.systock.timeout-segundos:20}") int timeoutSegundos) {
        this.mapper = mapper;
        this.habilitado = token != null && !token.isBlank();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Math.max(timeoutSegundos, 1) * 1000);
        fabrica.setReadTimeout(Math.max(timeoutSegundos, 1) * 1000);
        this.http = builder
                .baseUrl(url.endsWith("/") ? url.substring(0, url.length() - 1) : url)
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (token == null ? "" : token.trim()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    /** Pedidos de compra nos status informados (AB, CT, FN, PARA_EXPORTACAO, CL). */
    public List<SystockPedido> pedidos(List<String> status) {
        exigirHabilitado();
        StringBuilder uri = new StringBuilder("/compras/pedidos");
        for (int i = 0; i < status.size(); i++) {
            uri.append(i == 0 ? '?' : '&').append("status[]=").append(status.get(i));
        }
        try {
            List<SystockPedido> lista = http.get().uri(uri.toString()).retrieve()
                    .body(new ParameterizedTypeReference<List<SystockPedido>>() { });
            return lista == null ? List.of() : lista;
        } catch (RestClientException e) {
            log.warn("Falha ao consultar os pedidos no Systock: {}", e.getMessage());
            throw new SystockIndisponivelException(e);
        }
    }

    /** Um pedido; vazio se nao existir. */
    public Optional<SystockPedido> pedido(long id) {
        exigirHabilitado();
        try {
            // a rota devolve o pedido ou uma lista com ele: aceita os dois formatos
            JsonNode corpo = http.get().uri("/compras/pedidos/{id}", id).retrieve().body(JsonNode.class);
            if (corpo == null || corpo.isNull()) return Optional.empty();
            JsonNode no = corpo.isArray() ? (corpo.isEmpty() ? null : corpo.get(0)) : corpo;
            if (no == null || !no.hasNonNull("id_pedido")) return Optional.empty();
            return Optional.of(mapper.treeToValue(no, SystockPedido.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Falha ao consultar o pedido {} no Systock: {}", id, e.getMessage());
            throw new SystockIndisponivelException(e);
        }
    }

    private void exigirHabilitado() {
        if (!habilitado) {
            throw new SystockIndisponivelException(null);
        }
    }

    /** Systock fora do ar, sem token ou com resposta invalida. */
    public static class SystockIndisponivelException extends RuntimeException {
        public SystockIndisponivelException(Throwable causa) {
            super("Systock indisponivel", causa);
        }
    }
}
