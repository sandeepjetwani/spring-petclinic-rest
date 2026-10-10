package org.springframework.samples.petclinic.service.appointmentService;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"jpa", "hsqldb"})
class AppointmentServiceJpaTests extends AbstractAppointmentServiceTests {

    @Autowired
    EntityManager entityManager;

    @Override
    void clearCache() {
        // write pending changes first so the next read really comes from the database
        entityManager.flush();
        entityManager.clear();
    }
}
