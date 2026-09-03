package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Usuario;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Envio dos dados de acesso por e-mail. Fica desligado por padrao: so envia quando
 * app.email.habilitado = true e o servidor SMTP esta configurado (spring.mail.host).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /** Resultado do envio - nunca interrompe a criacao do usuario. */
    public record Resultado(boolean enviado, String aviso) {
        static Resultado ok() { return new Resultado(true, null); }
        static Resultado falha(String aviso) { return new Resultado(false, aviso); }
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean habilitado;
    private final String host;
    private final String remetente;
    private final String nomeRemetente;
    private final String copiaPara;
    private final String urlPortal;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.email.habilitado:false}") boolean habilitado,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${app.email.remetente:}") String remetente,
                        @Value("${app.email.nome-remetente:Portal do Fornecedor}") String nomeRemetente,
                        @Value("${app.email.copia-para:}") String copiaPara,
                        @Value("${app.portal.url:}") String urlPortal) {
        this.mailSender = mailSender;
        this.habilitado = habilitado;
        this.host = host;
        this.remetente = remetente;
        this.nomeRemetente = nomeRemetente;
        this.copiaPara = copiaPara;
        this.urlPortal = urlPortal;
    }

    public boolean isDisponivel() {
        return habilitado && host != null && !host.isBlank() && mailSender.getIfAvailable() != null;
    }

    public Resultado enviarAcesso(Usuario usuario, String senha) {
        if (!isDisponivel()) {
            return Resultado.falha("Envio de e-mail nao configurado. Repasse a senha manualmente.");
        }
        JavaMailSender sender = mailSender.getObject();
        try {
            MimeMessage mensagem = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente, nomeRemetente);
            helper.setTo(usuario.getEmail());
            if (copiaPara != null && !copiaPara.isBlank()) {
                helper.setCc(copiaPara.split(","));
            }
            helper.setSubject("Seu acesso ao Portal do Fornecedor");
            helper.setText(montarCorpo(usuario, senha), true);

            sender.send(mensagem);
            log.info("Dados de acesso enviados para {}", usuario.getEmail());
            return Resultado.ok();
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de acesso para {}: {}", usuario.getEmail(), e.getMessage());
            return Resultado.falha("Nao foi possivel enviar o e-mail (" + e.getMessage()
                    + "). Repasse a senha manualmente.");
        }
    }

    private String montarCorpo(Usuario usuario, String senha) {
        String fornecedor = usuario.getFornecedor() != null
                ? usuario.getFornecedor().getRazaoSocial() : null;
        Long codigo = usuario.getFornecedor() != null
                ? usuario.getFornecedor().getCodFornecedorErp() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#1f2328\">");
        sb.append("<div style=\"background:#b01116;color:#fff;padding:16px 20px;font-size:16px;font-weight:600\">")
          .append("Portal do Fornecedor &middot; Cadastro de Produtos</div>");
        sb.append("<div style=\"padding:20px\">");
        sb.append("<p>Ol&aacute;, ").append(escapar(usuario.getNome())).append("!</p>");
        sb.append("<p>Seu acesso ao portal de cadastro de produtos");
        if (fornecedor != null) {
            sb.append(" da <strong>").append(escapar(fornecedor)).append("</strong>");
        }
        sb.append(" foi liberado. Use os dados abaixo para entrar:</p>");

        sb.append("<table style=\"border-collapse:collapse;margin:16px 0\">");
        if (codigo != null) {
            linha(sb, "C&oacute;digo do fornecedor", String.valueOf(codigo));
        }
        linha(sb, "E-mail", escapar(usuario.getEmail()));
        linha(sb, "Senha", escapar(senha));
        sb.append("</table>");

        sb.append("<p>Voc&ecirc; pode entrar com o c&oacute;digo do fornecedor ou com o e-mail.</p>");
        if (urlPortal != null && !urlPortal.isBlank()) {
            sb.append("<p style=\"margin:22px 0\"><a href=\"").append(urlPortal)
              .append("\" style=\"background:#b01116;color:#fff;text-decoration:none;")
              .append("padding:11px 20px;border-radius:6px;display:inline-block\">Acessar o portal</a></p>");
            sb.append("<p style=\"color:#5b6572;font-size:12px\">Se o bot&atilde;o n&atilde;o funcionar, copie este endere&ccedil;o: ")
              .append(urlPortal).append("</p>");
        }
        sb.append("<p style=\"color:#5b6572;font-size:12px;border-top:1px solid #dfe3e8;padding-top:12px;margin-top:22px\">")
          .append("Guarde esta senha em local seguro e n&atilde;o a compartilhe. ")
          .append("Precisando de uma nova senha, fale com a equipe de cadastro. ")
          .append("Esta mensagem &eacute; autom&aacute;tica &mdash; n&atilde;o responda a este e-mail.</p>");
        sb.append("</div></div>");
        return sb.toString();
    }

    private void linha(StringBuilder sb, String rotulo, String valor) {
        sb.append("<tr><td style=\"padding:6px 16px 6px 0;color:#5b6572\">").append(rotulo)
          .append("</td><td style=\"padding:6px 0;font-weight:600;font-size:15px\">").append(valor)
          .append("</td></tr>");
    }

    private String escapar(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
