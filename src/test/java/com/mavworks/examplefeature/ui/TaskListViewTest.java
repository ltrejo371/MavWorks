package com.mavworks.examplefeature.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.notification.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class TaskListViewTest extends SpringBrowserlessTest {
    
}
