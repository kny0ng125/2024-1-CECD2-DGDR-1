import { CallRecordDto } from '@/types/callRecord';
import { Speaker } from '@/types/transcript';

export function resolveSpeaker(record: CallRecordDto): Speaker {
  return record.speaker === 'agent' ? 'agent' : 'caller';
}
