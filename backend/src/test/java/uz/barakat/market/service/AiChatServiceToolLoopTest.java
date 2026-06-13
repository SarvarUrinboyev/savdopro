package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.service.ai.AiProvider;

/**
 * Proves the agentic tool-calling loop actually fires — i.e. when the model
 * replies with a {@code TOOL ...} line, the service executes the tool and
 * feeds the result back before producing the final answer. Uses a scripted
 * stub provider (no network) injected over the real provider chain.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceToolLoopTest {

    @Mock ReportService reports;
    @Mock AnalyticsService analytics;
    @Mock SaleRepository sales;
    @Mock ProductRepository products;
    @Mock AiToolService tools;
    @Mock CustomerService customers;

    /** A provider whose {@code complete} returns the next scripted reply each call. */
    private static final class ScriptedProvider implements AiProvider {
        private final Deque<String> replies;
        ScriptedProvider(String... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }
        @Override public String name() { return "stub"; }
        @Override public boolean isConfigured() { return true; }
        @Override public String complete(String system, String user) {
            return replies.isEmpty() ? "" : replies.poll();
        }
    }

    private AiChatService newChatWith(AiProvider provider) throws Exception {
        // Real CfoActionService over mocked repos — with no ACTION lines in the
        // scripted replies it just returns the text unchanged (touches nothing).
        CfoActionService cfoActions = new CfoActionService(products, customers);
        AiChatService chat = new AiChatService(
                reports, analytics, sales, products, tools, cfoActions,
                "gemini", "", "m", "", "m", "", "m", "", "m");
        // Replace the (unconfigured) real chain with our scripted stub.
        Field f = AiChatService.class.getDeclaredField("chain");
        f.setAccessible(true);
        f.set(chat, List.of(provider));
        return chat;
    }

    @Test
    void model_tool_request_triggers_tool_execution_then_final_answer() throws Exception {
        // Keep the snapshot builder from NPEing on unmocked collaborators.
        when(analytics.profitByProduct(any(), any())).thenReturn(List.of());
        when(products.findLowStockProducts()).thenReturn(List.of());
        when(tools.catalog()).thenReturn("ASBOBLAR: hourlySales {from,to}");
        // The tool the model is expected to call:
        when(tools.call(eq("hourlySales"), any()))
                .thenReturn("19:00 -> 60 ta. Eng gavjum soat: 19:00 (60 ta)");

        // Turn 1: model asks for data. Turn 2: model answers from the result.
        AiChatService chat = newChatWith(new ScriptedProvider(
                "TOOL hourlySales {\"from\":\"2026-05-30\",\"to\":\"2026-05-30\"}",
                "Bugun eng ko'p savdo 19:00 da bo'lgan."));

        var resp = chat.ask(new AiChatService.ChatRequest(
                "Bugun qaysi soatda eng ko'p sotildi?", null));

        // The tool was actually invoked with the parsed name...
        verify(tools).call(eq("hourlySales"), any());
        // ...and the model's post-tool answer is what we return.
        assertThat(resp.answer()).contains("19:00");
        assertThat(resp.provider()).isEqualTo("stub");
    }

    @Test
    void plain_answer_skips_tools_entirely() throws Exception {
        when(analytics.profitByProduct(any(), any())).thenReturn(List.of());
        when(products.findLowStockProducts()).thenReturn(List.of());
        when(tools.catalog()).thenReturn("ASBOBLAR: hourlySales {from,to}");

        // Model answers directly — no TOOL line.
        AiChatService chat = newChatWith(new ScriptedProvider("Bugun 60 ta savdo bo'ldi."));

        var resp = chat.ask(new AiChatService.ChatRequest("Bugun nechta savdo?", null));

        assertThat(resp.answer()).contains("60 ta");
        verify(tools, never()).call(any(), any());
    }
}
