package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val db: AppDatabase) {
    val appConfig: Flow<AppConfigEntity?> = db.appConfigDao().getAppConfig()
    val allGroups: Flow<List<GroupEntity>> = db.groupDao().getAllGroups()
    val allStudents: Flow<List<StudentEntity>> = db.studentDao().getAllStudents()
    val allCompetitions: Flow<List<CompetitionEntity>> = db.competitionDao().getAllCompetitions()

    suspend fun saveAppConfig(config: AppConfigEntity) {
        db.appConfigDao().insertAppConfig(config)
    }

    suspend fun insertGroup(group: GroupEntity) {
        db.groupDao().insertGroup(group)
    }

    suspend fun updateGroup(group: GroupEntity) {
        db.groupDao().updateGroup(group)
    }

    suspend fun deleteGroup(group: GroupEntity) {
        db.groupDao().deleteGroup(group)
    }

    suspend fun insertStudent(student: StudentEntity) {
        db.studentDao().insertStudent(student)
    }

    suspend fun insertStudents(students: List<StudentEntity>) {
        db.studentDao().insertStudents(students)
    }

    suspend fun deleteStudent(student: StudentEntity) {
        db.studentDao().deleteStudent(student)
    }

    suspend fun insertCompetition(competition: CompetitionEntity) {
        db.competitionDao().insertCompetition(competition)
    }

    suspend fun updateCompetition(competition: CompetitionEntity) {
        db.competitionDao().updateCompetition(competition)
    }

    suspend fun deleteCompetition(competition: CompetitionEntity) {
        db.competitionDao().deleteCompetition(competition)
    }
}
