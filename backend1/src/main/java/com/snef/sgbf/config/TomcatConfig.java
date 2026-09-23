package com.snef.sgbf.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Force le connecteur Tomcat embarque a utiliser le protocole NIO2
 * ({@link Http11Nio2Protocol}, base sur {@code AsynchronousChannelGroup}/IOCP)
 * plutot que le NIO classique ({@code Selector}/{@code Pipe}) utilise par
 * defaut.
 *
 * <p>Necessaire uniquement sur ce poste de developpement : le JDK 21 sur
 * Windows introduit {@code WEPollSelectorImpl}, une implementation de
 * {@code Selector} qui cree son "pipe" de reveil interne via un socket de
 * domaine Unix (AF_UNIX). Dans cet environnement, l'ouverture de ce socket
 * echoue ({@code SocketException: Invalid argument: connect}), ce qui
 * empeche tout connecteur NIO classique de demarrer - y compris avant
 * qu'aucune requete HTTP ne soit traitee. Le connecteur NIO2 ne passe pas par
 * cette voie et demarre normalement. Cette configuration est sans risque a
 * conserver meme sur un autre poste : NIO2 est un connecteur Tomcat standard
 * et pleinement supporte, pas un contournement fragile.
 */
@Configuration
public class TomcatConfig {

    @org.springframework.context.annotation.Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> connecteurNio2() {
        return factory -> factory.setProtocol(Http11Nio2Protocol.class.getName());
    }
}
