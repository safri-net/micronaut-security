/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.authentication;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.token.config.TokenConfiguration;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * An implementation of {@link AuthenticationFetcher} that decodes a username
 * and password from the Authorization header and authenticates the credentials
 * against any {@link AuthenticationProvider}s available.
 */
@Requires(property = BasicAuthAuthenticationConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Singleton
public class BasicAuthAuthenticationFetcher implements AuthenticationFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthAuthenticationFetcher.class);
    private final Authenticator authenticator;
    private final TokenConfiguration configuration;

    /**
     * @param authenticator The authenticator to authenticate the credentials
     * @param configuration The basic authentication configuration
     */
    public BasicAuthAuthenticationFetcher(Authenticator authenticator,
                                          TokenConfiguration configuration) {
        this.authenticator = authenticator;
        this.configuration = configuration;
    }

    @Override
    public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        Optional<UsernamePasswordCredentials> credentials = request.getHeaders().getAuthorization().flatMap(this::parseCredentials);

        if (credentials.isPresent()) {
            Flowable<AuthenticationResponse> authenticationResponse = Flowable.fromPublisher(authenticator.authenticate(request, credentials.get()));

            return authenticationResponse.switchMap(response -> {
                if (response.isAuthenticated()) {
                    UserDetails userDetails = response.getUserDetails().get();
                    return Flowable.just(new AuthenticationUserDetailsAdapter(userDetails, configuration.getRolesName(), configuration.getNameKey()));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Could not authenticate {}", credentials.get().getUsername());
                    }
                    return Flowable.empty();
                }
            });

        } else {
            return Publishers.empty();
        }
    }

    /**
     *
     * @param authorization Authorization HTTP Header value
     * @return Extracted Credentials as a {@link UsernamePasswordCredentials} or an empty optional if not possible.
     */
    @Deprecated
    @NonNull
    public Optional<UsernamePasswordCredentials> parseCredentials(@NonNull String authorization) {
        return BasicAuthUtils.parseCredentials(authorization);
    }
}
