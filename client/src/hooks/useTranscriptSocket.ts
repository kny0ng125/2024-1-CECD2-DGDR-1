import { useEffect, useRef } from 'react';
import { useCallStore } from '@/stores/useCallStore';
import { useAuthStore } from '@/stores/useAuthStore';
import { TranscriptMessage } from '@/types/transcript';
import { WS_BASE_URL } from '@/lib/config';

export function useTranscriptSocket() {
  const retryDelayRef = useRef(1000);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let retryTimer: number | undefined;
    let cancelled = false;

    const connect = () => {
      if (cancelled) return;
      const token = useAuthStore.getState().accessToken;
      if (!token) return;

      const ws = new WebSocket(`${WS_BASE_URL}/ws/transcript?token=${token}`);
      wsRef.current = ws;

      ws.onopen = () => {
        useCallStore.getState().setWsConnected(true);
        retryDelayRef.current = 1000;
      };

      ws.onmessage = (event) => {
        let msg: TranscriptMessage;
        try { msg = JSON.parse(event.data); } catch { return; }

        const store = useCallStore.getState();
        switch (msg.type) {
          case 'call_event':
            if (msg.event === 'started') store.startCall(msg.callId, msg.timestamp);
            else store.endCall();
            break;
          case 'transcript':
            if (msg.status === 'partial') store.setPartial(msg.text, msg.speaker);
            else {
              store.addFinalConversation({
                text: msg.text, speaker: msg.speaker, timestamp: msg.timestamp,
              });
              store.clearPartial();
            }
            break;
          case 'save_prompt':
            store.openSavePrompt({
              callId: msg.callId, recordCount: msg.recordCount, duration: msg.duration,
            });
            break;
        }
      };

      ws.onclose = () => {
        useCallStore.getState().setWsConnected(false);
        if (cancelled) return;
        retryTimer = window.setTimeout(connect, retryDelayRef.current);
        retryDelayRef.current = Math.min(retryDelayRef.current * 2, 8000);
      };

      ws.onerror = () => { /* onclose가 이어서 호출됨 */ };
    };

    connect();
    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      wsRef.current?.close();
    };
  }, []);
}
