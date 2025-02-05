package com.transformuk.hee.tis.tcs.service.listener.person;

import static org.mockito.Mockito.verify;

import com.transformuk.hee.tis.tcs.api.dto.PersonDTO;
import com.transformuk.hee.tis.tcs.api.dto.ProgrammeMembershipDTO;
import com.transformuk.hee.tis.tcs.service.event.ProgrammeMembershipCreatedEvent;
import com.transformuk.hee.tis.tcs.service.event.ProgrammeMembershipDeletedEvent;
import com.transformuk.hee.tis.tcs.service.event.ProgrammeMembershipSavedEvent;
import com.transformuk.hee.tis.tcs.service.service.PersonElasticSearchService;
import com.transformuk.hee.tis.tcs.service.service.RevalidationRabbitService;
import com.transformuk.hee.tis.tcs.service.service.RevalidationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProgrammeMembershipEventListenerTest {

  private ProgrammeMembershipSavedEvent savedEvent;
  private ProgrammeMembershipCreatedEvent createdEvent;
  private ProgrammeMembershipDeletedEvent deletedEvent;
  private ProgrammeMembershipDTO source;
  private PersonDTO person;
  private static final Long PERSONID = Long.valueOf(11111111);
  private static final Long PROGRAMME_MEMBERSHIP_ID = Long.valueOf(22222222);

  @Mock
  RevalidationRabbitService revalidationRabbitService;

  @Mock
  PersonElasticSearchService personElasticSearchService;

  @Mock
  RevalidationService revalidationService;

  @InjectMocks
  ProgrammeMembershipEventListener testObj;

  @Before
  public void setup() {
    person = new PersonDTO();
    person.setId(PERSONID);

    source = new ProgrammeMembershipDTO();
    source.setId(PROGRAMME_MEMBERSHIP_ID);
    source.setPerson(person);

    savedEvent = new ProgrammeMembershipSavedEvent(source);
    createdEvent = new ProgrammeMembershipCreatedEvent(source);
    deletedEvent = new ProgrammeMembershipDeletedEvent(source);
  }

  @Test
  public void shouldHandleProgrammeMembershipSavedEvent() {
    testObj.handleProgrammeMembershipSavedEvent(savedEvent);
    verify(personElasticSearchService).updatePersonDocument(PERSONID);
    verify(revalidationRabbitService).updateReval(revalidationService.buildTcsConnectionInfo(PERSONID));
  }

  @Test
  public void shouldHandleProgrammeMembershipCreatedEvent() {
    testObj.handleProgrammeMembershipCreatedEvent(createdEvent);
    verify(personElasticSearchService).updatePersonDocument(PERSONID);
    verify(revalidationRabbitService).updateReval(revalidationService.buildTcsConnectionInfo(PERSONID));
  }

  @Test
  public void shouldHandleProgrammeMembershipDeletedEvent() {
    testObj.handleProgrammeMembershipDeletedEvent(deletedEvent);
    verify(personElasticSearchService).deletePersonDocument(PERSONID);
    verify(revalidationRabbitService).updateReval(revalidationService.buildTcsConnectionInfo(PERSONID));
  }
}
