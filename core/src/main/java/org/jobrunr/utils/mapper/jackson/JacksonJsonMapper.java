package org.jobrunr.utils.mapper.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.jobrunr.JobRunrError;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.states.JobState;
import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson.modules.JobRunrTimeModule;
import org.jobrunr.utils.metadata.VersionRetriever;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Map;

import static org.jobrunr.utils.reflection.ReflectionUtils.newInstanceOrElse;

public class JacksonJsonMapper implements JsonMapper {

    private final ObjectMapper objectMapper;

    public JacksonJsonMapper() {
        if (VersionRetriever.getVersion(ObjectMapper.class).compareTo("2.11.2") > 0) {
            throw new UnsupportedOperationException("JobRunr currently does not support a Jackson version greater than 2.11.2");
        }
        objectMapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .registerModule(getModule())
                .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"))
                .activateDefaultTypingAsProperty(BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(JobState.class)
                                .allowIfBaseType(Map.class)
                                .allowIfBaseType(JobContext.Metadata.class)
                                .build(),
                        ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS,
                        "@class");
    }

    @Override
    public String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw JobRunrError.canNotHappenError(e);
        }
    }

    @Override
    public void serialize(Writer writer, Object object) {
        try {
            objectMapper.writeValue(writer, object);
        } catch (IOException e) {
            throw JobRunrError.canNotHappenError(e);
        }
    }

    @Override
    public void serialize(OutputStream outputStream, Object object) {
        try {
            objectMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw JobRunrError.canNotHappenError(e);
        }
    }

    @Override
    public <T> T deserialize(String serializedObjectAsString, Class<T> clazz) {
        try {
            return objectMapper.readValue(serializedObjectAsString, clazz);
        } catch (InvalidDefinitionException e) {
            throw JobRunrException.configurationException("Did you register all necessary Jackson Modules?", e);
        } catch (IOException e) {
            throw JobRunrError.canNotHappenError(e);
        }
    }

    protected com.fasterxml.jackson.databind.Module getModule() {
        return newInstanceOrElse("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", new JobRunrTimeModule());
    }
}
