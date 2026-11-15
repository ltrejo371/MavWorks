package com.mavworks;

import com.mavworks.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextModerationServiceTest {
    @Test void rejectsProfanitySlursAndCommonDisguises() {
        for (String text : new String[] {"fuck you", "This was SHIT!", "sh1t", "b!tch", "f.u.c.k",
                "f u c k", "f\u200Buck", "\uff46\uff55\uff43\uff4b", "fuuck", "fuck123",
                "you asshole", "motherfucker", "nigger", "faggot", "heil hitler"}) {
            assertTrue(TextModerationService.containsProhibitedLanguage(text), text);
        }
    }
    @Test void allowsOrdinaryWordsIdentityTermsAndCriticalReviews() {
        for (String text : new String[] {"class assignment", "Scunthorpe", "assistant", "classic bass guitar",
                "Dickinson textbook", "cocktail dress", "document", "Saturday", "hello@school.edu",
                "gay", "transgender", "Black students", "The service was poor and I want a refund.", ""}) {
            assertFalse(TextModerationService.containsProhibitedLanguage(text), text);
        }
        assertFalse(TextModerationService.containsProhibitedLanguage(null));
    }
    @Test void errorNamesFieldWithoutRepeatingSubmittedText() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> TextModerationService.validate("Message", "fuck you private text"));
        assertTrue(error.getMessage().startsWith("Message"));
        assertFalse(error.getMessage().contains("private text"));
        assertFalse(error.getMessage().contains("fuck"));
    }
    @Test void registrationChecksUsernameButNotPasswordOrEmail() {
        var errors = RegistrationValidation.errors("fuck", "student@mavs.uta.edu", "Password123", "Password123");
        assertTrue(errors.containsKey(RegistrationValidation.Field.USERNAME));
        assertTrue(RegistrationValidation.errors("student", "shit@mavs.uta.edu", "fuckPassword123", "fuckPassword123").isEmpty());
    }
}
