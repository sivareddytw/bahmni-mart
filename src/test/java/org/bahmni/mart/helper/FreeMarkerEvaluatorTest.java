package org.bahmni.mart.helper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.bahmni.mart.exception.BatchResourceException;
import org.bahmni.mart.form.domain.BahmniForm;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.StringWriter;
import java.util.HashMap;

import static org.bahmni.mart.CommonTestHelper.setValuesForMemberFields;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

@PrepareForTest({FreeMarkerEvaluator.class})
@RunWith(PowerMockRunner.class)
public class FreeMarkerEvaluatorTest {

    private FreeMarkerEvaluator freeMarkerEvaluator;

    @Mock
    private Configuration configuration;

    @Rule
    private ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        freeMarkerEvaluator = new FreeMarkerEvaluator();
        setValuesForMemberFields(freeMarkerEvaluator, "configuration", configuration);
    }

    @Test
    public void shouldEvaluateTemplate() throws Exception {
        BahmniForm bahmniForm = new BahmniForm();
        String templateName = "Vital Signs";
        StringWriter stringWriter = Mockito.mock(StringWriter.class);
        PowerMockito.whenNew(StringWriter.class).withNoArguments().thenReturn(stringWriter);
        Template template = Mockito.mock(Template.class);
        when(configuration.getTemplate(templateName)).thenReturn(template);
        HashMap<String, Object> hashMap = mock(HashMap.class);
        PowerMockito.whenNew(HashMap.class).withNoArguments().thenReturn(hashMap);
        String expectedOutput = "outputValue";
        when(stringWriter.toString()).thenReturn(expectedOutput);

        String actualOutput = freeMarkerEvaluator.evaluate(templateName, bahmniForm);

        assertEquals(expectedOutput, actualOutput);
        verify(configuration, times(1)).getTemplate(templateName);
        verify(hashMap, times(1)).put("input", bahmniForm);
        verify(template, times(1)).process(hashMap, stringWriter);
    }

    @Test
    public void shouldThrowBatchResourceException() throws Exception {
        BahmniForm bahmniForm = new BahmniForm();
        String templateName = "Vital Signs";
        BatchResourceException batchResourceException = Mockito.mock(BatchResourceException.class);
        when(configuration.getTemplate(templateName)).thenThrow(batchResourceException);

        expectedException.expect(BatchResourceException.class);
        expectedException.expectMessage(
                String.format("Unable to continue generating a the template with name [%s]", templateName));

        freeMarkerEvaluator.evaluate(templateName, bahmniForm);
        verify(configuration, times(1)).getTemplate(templateName);
    }
}
