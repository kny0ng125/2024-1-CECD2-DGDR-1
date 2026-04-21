import { CallRecordDto } from '@/types/callRecord';
import { Speaker } from '@/types/transcript';

export function resolveSpeaker(record: CallRecordDto): Speaker {
  return record.speakerPhoneNumber === record.call.user.phoneNumber
    ? 'agent'
    : 'caller';
}
