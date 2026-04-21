export type Speaker = 'caller' | 'agent';

export type TranscriptMessage =
  | { type: 'transcript'; status: 'partial' | 'final';
      text: string; speaker: Speaker; timestamp: string }
  | { type: 'call_event'; event: 'started' | 'ended';
      callId: number; timestamp: string }
  | { type: 'save_prompt'; callId: number; recordCount: number;
      duration: string };

export interface Conversation {
  id: string;
  text: string;
  speaker: Speaker;
  timestamp: string;
}

export interface SavePromptData {
  callId: number;
  recordCount: number;
  duration: string;
}
