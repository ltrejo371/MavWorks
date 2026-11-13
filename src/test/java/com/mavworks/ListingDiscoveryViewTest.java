package com.mavworks;

import com.mavworks.landing.ui.LandingView;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingSort;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingDiscoveryViewTest {
    @Test @SuppressWarnings("unchecked")
    void datePresetsAndCustomRangeCombineWithSorting() {
        var current = mock(CurrentUser.class);
        when(current.username()).thenReturn(Optional.of("buyer"));
        var catalog = mock(ListingCatalogService.class);
        when(catalog.search(any(), any())).thenReturn(List.of());
        var view = new LandingView(current, catalog, mock(ImageStorageService.class));
        var posted = (ComboBox<String>) components(view).filter(c -> c instanceof ComboBox<?> b && b.getLabel().equals("Posted")).findFirst().orElseThrow();
        var sort = (ComboBox<ListingSort>) components(view).filter(c -> c instanceof ComboBox<?> b && b.getLabel().equals("Sort by")).findFirst().orElseThrow();
        var dates = components(view).filter(DatePicker.class::isInstance).map(DatePicker.class::cast).toList();
        var zone = ZoneId.of("America/Chicago");
        var today = LocalDate.now(zone);
        for (String preset : List.of("Today", "Past week", "Past month", "Past year")) {
            posted.setValue(preset);
            var filter = lastFilter(catalog);
            var expected = switch (preset) {
                case "Past week" -> today.minusWeeks(1);
                case "Past month" -> today.minusMonths(1);
                case "Past year" -> today.minusYears(1);
                default -> today;
            };
            assertEquals(expected.atStartOfDay(zone).toInstant(), filter.from());
            assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), filter.until());
        }
        posted.setValue("Custom range");
        assertTrue(dates.stream().allMatch(Component::isVisible));
        dates.get(0).setValue(LocalDate.of(2026, 11, 1));
        dates.get(1).setValue(LocalDate.of(2026, 11, 1));
        sort.setValue(ListingSort.MOST_VIEWED);
        var filter = lastFilter(catalog);
        assertEquals(ListingSort.MOST_VIEWED, filter.sort());
        assertEquals(25, Duration.between(filter.from(), filter.until()).toHours());
        clearInvocations(catalog);
        dates.get(1).setValue(LocalDate.of(2026, 10, 31));
        verifyNoInteractions(catalog);
        posted.setValue("All time");
        assertTrue(dates.stream().noneMatch(Component::isVisible));
        assertEquals(Instant.EPOCH, lastFilter(catalog).from());
    }
    private SearchFilter lastFilter(ListingCatalogService catalog) {
        var capture = ArgumentCaptor.forClass(SearchFilter.class);
        verify(catalog, atLeastOnce()).search(capture.capture(), any());
        return capture.getValue();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}
