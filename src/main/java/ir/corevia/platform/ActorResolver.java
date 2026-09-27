package ir.corevia.platform;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ActorResolver {

    public String resolve(Authentication authentication, String localActorHeader) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        if (localActorHeader != null && !localActorHeader.isBlank()) {
            return localActorHeader.strip();
        }
        return "local-system";
    }
}
