export interface User {
  id: string;
  name: string;
  phoneNumber: string;
}

export interface Call {
  id: number;
  startTime: string;
  user: User;
}

export interface CallRecordDto {
  id: number;
  call: Call;
  speakerPhoneNumber: string;
  transcription: string;
  time: string;
}

export interface CallDto {
  id: number;
  startTime: string;
  user: User;
}
