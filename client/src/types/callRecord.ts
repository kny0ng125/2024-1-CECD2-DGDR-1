export interface User {
  id: string;
  name: string;
  phone: string;
}

export interface Call {
  id: number;
  startTime: string;
  user: User;
}

export interface CallRecordDto {
  id: number;
  speaker: 'agent' | 'caller';
  speakerPhoneNumber: string;
  transcription: string;
  time: string;
}

export interface CallDto {
  id: number;
  startTime: string;
  user: User;
}
