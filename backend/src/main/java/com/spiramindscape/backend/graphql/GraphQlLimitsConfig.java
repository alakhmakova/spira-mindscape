package com.spiramindscape.backend.graphql;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * How much work one {@code POST /graphql} may ask for.
 *
 * <p>{@code RateLimitFilter} counts <b>requests</b>, and a GraphQL request is not a unit of work:
 * one body can nest {@code goals { targets { items … } } } as deep as the schema allows, or carry
 * five hundred aliased copies of the same mutation — {@code a1: createGoal(…) a2: createGoal(…) …}
 * — and still be one request against a limit of 120 a minute. These three caps make the request
 * the unit the rate limit already assumes.
 *
 * <p>The numbers are far above anything the clients send, so they never bite a real user. The
 * web's largest operation is {@code Goals} (≈70 fields, depth 4), Android's {@code GetGoal} is
 * smaller, and every mutation either client sends has exactly one root field. Depth 15 also leaves
 * room for the introspection query the {@code local} profile's tooling runs (depth ≈13).
 *
 * <p>graphql-java already bounds the <i>text</i> of a document (its parser stops at 15 000 tokens),
 * and Cloud Run bounds the body (32 MiB), so these are the remaining amplifiers.
 */
@Configuration
public class GraphQlLimitsConfig {

    @Bean
    public Instrumentation maxQueryDepth(@Value("${spira.graphql.max-depth:15}") int maxDepth) {
        return new MaxQueryDepthInstrumentation(maxDepth);
    }

    @Bean
    public Instrumentation maxQueryComplexity(
            @Value("${spira.graphql.max-complexity:500}") int maxComplexity) {
        return new MaxQueryComplexityInstrumentation(maxComplexity);
    }

    @Bean
    public Instrumentation maxMutationFields(
            @Value("${spira.graphql.max-mutation-fields:10}") int maxFields) {
        return new MaxMutationFieldsInstrumentation(maxFields);
    }

    /**
     * Refuses a mutation operation with more than {@code max} root fields — the aliasing
     * amplifier, where each alias is a separate write. Fragments are followed, so spreading the
     * fields through {@code ...F} counts the same as writing them out.
     */
    static final class MaxMutationFieldsInstrumentation extends SimplePerformantInstrumentation {
        private final int max;

        MaxMutationFieldsInstrumentation(int max) {
            this.max = max;
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginExecuteOperation(
                InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
            var context = parameters.getExecutionContext();
            OperationDefinition operation = context.getOperationDefinition();
            if (operation.getOperation() == OperationDefinition.Operation.MUTATION) {
                int fields = count(operation.getSelectionSet(), context.getFragmentsByName(), 0);
                if (fields > max) {
                    throw new AbortExecutionException(
                            "Too many mutations in one request (" + fields + " > " + max + ")");
                }
            }
            return super.beginExecuteOperation(parameters, state);
        }

        private static int count(SelectionSet set, Map<String, FragmentDefinition> fragments, int depth) {
            // A fragment cycle is a validation error already; the depth guard only keeps this
            // walk finite if one ever reached it.
            if (set == null || depth > 20) return 0;
            int n = 0;
            for (Selection<?> selection : set.getSelections()) {
                if (selection instanceof InlineFragment inline) {
                    n += count(inline.getSelectionSet(), fragments, depth + 1);
                } else if (selection instanceof FragmentSpread spread) {
                    FragmentDefinition fragment = fragments.get(spread.getName());
                    n += fragment == null ? 0 : count(fragment.getSelectionSet(), fragments, depth + 1);
                } else {
                    n++;
                }
            }
            return n;
        }
    }
}
