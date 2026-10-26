export interface CourseSummary {
  id: number;
  code: string;
  title: string;
  credits: number;
}

export interface Student {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string | null;
  courses: CourseSummary[];
}

export interface StudentRequest {
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string | null;
  courseIds?: number[] | null;
}

export interface StudentSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
}

export interface Course {
  id: number;
  code: string;
  title: string;
  description: string | null;
  credits: number;
  capacity: number;
  enrolledCount: number;
}

export interface CourseRequest {
  code: string;
  title: string;
  description: string | null;
  credits: number;
  capacity: number;
}
