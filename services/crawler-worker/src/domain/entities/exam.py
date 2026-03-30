from datetime import datetime, date
from uuid import UUID, uuid4
from typing import Optional, List
from pydantic import BaseModel, Field, HttpUrl, ConfigDict


class ExamDocument(BaseModel):
    title: str
    url: str

class Exam(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID = Field(default_factory=uuid4)
    institution: str = Field()

    url: HttpUrl = Field()

    status: str = Field(default="PENDING")

    documents: List[ExamDocument] = []
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)