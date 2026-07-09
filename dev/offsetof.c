// Struct offset probe for libyaml event/mark structs.
// Run:  cc -o offsetof dev/offsetof.c -lyaml && ./offsetof
// The output (struct field offsets + enum values) must be copied into
// src/jolt/yaml/ffi.clj when upgrading libyaml or porting to a new arch.
//
#include <stdio.h>
#include <stddef.h>
#include <yaml.h>

int main() {
    printf("=== yaml_mark_t (%zu bytes) ===\n", sizeof(yaml_mark_t));
    printf("  index  %2zu\n", offsetof(yaml_mark_t, index));
    printf("  line   %2zu\n", offsetof(yaml_mark_t, line));
    printf("  column %2zu\n", offsetof(yaml_mark_t, column));

    printf("\n=== yaml_event_t (%zu bytes) ===\n", sizeof(yaml_event_t));
    printf("  type       %2zu\n", offsetof(yaml_event_t, type));
    printf("  data       %2zu\n", offsetof(yaml_event_t, data));
    printf("  start_mark %2zu\n", offsetof(yaml_event_t, start_mark));
    printf("  end_mark   %2zu\n", offsetof(yaml_event_t, end_mark));

    printf("\n=== data.scalar sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  scalar.anchor         %2zu\n", (size_t)&e.data.scalar.anchor - b);
        printf("  scalar.tag            %2zu\n", (size_t)&e.data.scalar.tag - b);
        printf("  scalar.value          %2zu\n", (size_t)&e.data.scalar.value - b);
        printf("  scalar.length         %2zu\n", (size_t)&e.data.scalar.length - b);
        printf("  scalar.plain_implicit  %2zu\n", (size_t)&e.data.scalar.plain_implicit - b);
        printf("  scalar.quoted_implicit %2zu\n", (size_t)&e.data.scalar.quoted_implicit - b);
        printf("  scalar.style          %2zu\n", (size_t)&e.data.scalar.style - b);
    }

    printf("\n=== data.alias sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  alias.anchor %2zu\n", (size_t)&e.data.alias.anchor - b);
    }

    printf("\n=== data.sequence_start sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  seq_start.anchor %2zu\n", (size_t)&e.data.sequence_start.anchor - b);
        printf("  seq_start.tag    %2zu\n", (size_t)&e.data.sequence_start.tag - b);
        printf("  seq_start.implicit %2zu\n", (size_t)&e.data.sequence_start.implicit - b);
        printf("  seq_start.style  %2zu\n", (size_t)&e.data.sequence_start.style - b);
    }

    printf("\n=== data.mapping_start sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  map_start.anchor %2zu\n", (size_t)&e.data.mapping_start.anchor - b);
        printf("  map_start.tag    %2zu\n", (size_t)&e.data.mapping_start.tag - b);
        printf("  map_start.implicit %2zu\n", (size_t)&e.data.mapping_start.implicit - b);
        printf("  map_start.style  %2zu\n", (size_t)&e.data.mapping_start.style - b);
    }

    printf("\n=== data.stream_start sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  stream_start.encoding %2zu\n", (size_t)&e.data.stream_start.encoding - b);
    }

    printf("\n=== data.document_start sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  doc_start.version_directive %2zu\n", (size_t)&e.data.document_start.version_directive - b);
        printf("  doc_start.tag_directives    %2zu\n", (size_t)&e.data.document_start.tag_directives - b);
        printf("  doc_start.implicit          %2zu\n", (size_t)&e.data.document_start.implicit - b);
    }

    printf("\n=== data.document_end sub-offsets ===\n");
    {
        yaml_event_t e; size_t b = (size_t)&e.data;
        printf("  doc_end.implicit %2zu\n", (size_t)&e.data.document_end.implicit - b);
    }

    printf("\n=== Event type values ===\n");
    printf("  YAML_NO_EVENT              %d\n", YAML_NO_EVENT);
    printf("  YAML_STREAM_START_EVENT    %d\n", YAML_STREAM_START_EVENT);
    printf("  YAML_STREAM_END_EVENT      %d\n", YAML_STREAM_END_EVENT);
    printf("  YAML_DOCUMENT_START_EVENT  %d\n", YAML_DOCUMENT_START_EVENT);
    printf("  YAML_DOCUMENT_END_EVENT    %d\n", YAML_DOCUMENT_END_EVENT);
    printf("  YAML_ALIAS_EVENT           %d\n", YAML_ALIAS_EVENT);
    printf("  YAML_SCALAR_EVENT          %d\n", YAML_SCALAR_EVENT);
    printf("  YAML_SEQUENCE_START_EVENT  %d\n", YAML_SEQUENCE_START_EVENT);
    printf("  YAML_SEQUENCE_END_EVENT    %d\n", YAML_SEQUENCE_END_EVENT);
    printf("  YAML_MAPPING_START_EVENT   %d\n", YAML_MAPPING_START_EVENT);
    printf("  YAML_MAPPING_END_EVENT     %d\n", YAML_MAPPING_END_EVENT);

    printf("\n=== Style values ===\n");
    printf("  YAML_PLAIN_SCALAR_STYLE       %d\n", YAML_PLAIN_SCALAR_STYLE);
    printf("  YAML_SINGLE_QUOTED_SCALAR_STYLE %d\n", YAML_SINGLE_QUOTED_SCALAR_STYLE);
    printf("  YAML_DOUBLE_QUOTED_SCALAR_STYLE %d\n", YAML_DOUBLE_QUOTED_SCALAR_STYLE);
    printf("  YAML_LITERAL_SCALAR_STYLE      %d\n", YAML_LITERAL_SCALAR_STYLE);
    printf("  YAML_FOLDED_SCALAR_STYLE       %d\n", YAML_FOLDED_SCALAR_STYLE);
    printf("  YAML_BLOCK_SEQUENCE_STYLE      %d\n", YAML_BLOCK_SEQUENCE_STYLE);
    printf("  YAML_FLOW_SEQUENCE_STYLE       %d\n", YAML_FLOW_SEQUENCE_STYLE);
    printf("  YAML_BLOCK_MAPPING_STYLE       %d\n", YAML_BLOCK_MAPPING_STYLE);
    printf("  YAML_FLOW_MAPPING_STYLE        %d\n", YAML_FLOW_MAPPING_STYLE);

    printf("\n=== Parse test ===\n");
    {
        yaml_parser_t parser;
        yaml_event_t event;
        const char *input = "hello\n";
        if (!yaml_parser_initialize(&parser)) {
            printf("ERROR: yaml_parser_initialize failed\n");
            return 1;
        }
        yaml_parser_set_input_string(&parser, (const unsigned char*)input, strlen(input));
        for (int i = 0; i < 10; i++) {
            if (!yaml_parser_parse(&parser, &event)) {
                printf("ERROR: parse failed at event %d\n", i);
                break;
            }
            printf("  event %d: type=%d", i, event.type);
            if (event.type == YAML_SCALAR_EVENT)
                printf(" value='%s' len=%zu style=%d",
                       event.data.scalar.value, event.data.scalar.length, event.data.scalar.style);
            if (event.type == YAML_ALIAS_EVENT)
                printf(" anchor='%s'", event.data.alias.anchor);
            if (event.type == YAML_SEQUENCE_START_EVENT || event.type == YAML_MAPPING_START_EVENT)
                printf(" anchor='%s' tag='%s'",
                       event.data.sequence_start.anchor, event.data.sequence_start.tag);
            printf(" mark=(%zu,%zu,%zu)-(%zu,%zu,%zu)\n",
                   event.start_mark.index, event.start_mark.line, event.start_mark.column,
                   event.end_mark.index, event.end_mark.line, event.end_mark.column);
            yaml_event_delete(&event);
            if (event.type == YAML_STREAM_END_EVENT) break;
        }
        yaml_parser_delete(&parser);
    }
    return 0;
}
