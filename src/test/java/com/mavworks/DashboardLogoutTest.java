package com.mavworks;

import com.mavworks.dashboard.DashboardView;
import com.mavworks.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.mockito.Mockito.*;

class DashboardLogoutTest {
    @Test void logoutUsesAuthenticationContextEvenWhenAccountLoadingFails() {
        var account = mock(AccountDashboardService.class);
        when(account.load()).thenThrow(new DataAccessResourceFailureException("offline"));
        var authentication = mock(AuthenticationContext.class);
        var view = new DashboardView(account, mock(AccountEditingService.class), mock(LocalImageStorageService.class),
                mock(ListingManagementService.class), authentication, mock(BlockService.class), mock(ReportService.class));
        var logout = components(view).filter(c -> c instanceof Button b && b.getText().equals("Logout"))
                .map(Button.class::cast).findFirst().orElseThrow();
        logout.click();
        verify(authentication).logout();
    }
    private Stream<Component> components(Component root) { return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components)); }
}
