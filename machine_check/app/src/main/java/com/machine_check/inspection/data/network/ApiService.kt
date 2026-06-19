package com.machine_check.inspection.data.network

import com.machine_check.inspection.data.models.FrequenciesAvailableResponse
import com.machine_check.inspection.data.models.FullInspectionRequest
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.models.MonthlyPhoto
import com.machine_check.inspection.data.models.PhotoUploadResponse
import com.machine_check.inspection.data.models.SaveDailyRecordRequest
import com.machine_check.inspection.data.models.SaveRecordResponse
import com.machine_check.inspection.data.models.SubmitResponse
import com.machine_check.inspection.data.models.UninspectedMandatoryResponse
import com.machine_check.inspection.data.models.UninspectedMonthlyResponse
import com.machine_check.inspection.data.models.ValidateResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 点检 API 接口定义
 */
interface ApiService {

    /** 根据设备型号获取点检模板列表 */
    @GET("/api/Inspection/templates/{deviceModel}")
    suspend fun getTemplates(
        @Path("deviceModel") deviceModel: String,
        @Query("frequency") frequency: String = "日"
    ): Response<List<InspectionTemplate>>

    /** 提交完整点检记录 */
    @POST("/api/Inspection/submit-full")
    suspend fun submitFullInspection(
        @Body request: FullInspectionRequest
    ): Response<SubmitResponse>

    /** 验证工号是否为点检资格人员 */
    @GET("/api/Inspection/operators/validate/{employeeId}")
    suspend fun validateOperator(
        @Path("employeeId") employeeId: String
    ): Response<ValidateResponse>

    /** 获取设备各频率可用状态 */
    @GET("/api/Inspection/frequencies-available")
    suspend fun getFrequenciesAvailable(
        @Query("deviceModel") deviceModel: String
    ): Response<FrequenciesAvailableResponse>

    /** 获取未点检的必须点检设备 location 列表 */
    @GET("/api/Inspection/uninspected-mandatory-locations")
    suspend fun getUninspectedMandatoryLocations(): Response<UninspectedMandatoryResponse>

    /** 获取当月完全未点检的设备清单（所有频率均无记录） */
    @GET("/api/Inspection/uninspected-monthly")
    suspend fun getUninspectedMonthly(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<UninspectedMonthlyResponse>

    // ==================== 照片功能端点 ====================

    /** 保存每日点检记录（返回 recordIds + pendingPhotoItems） */
    @POST("/api/Inspection/records/save")
    suspend fun saveDailyRecord(
        @Body request: SaveDailyRecordRequest
    ): Response<SaveRecordResponse>

    /** 上传照片（multipart） */
    @Multipart
    @POST("/api/Inspection/photos/upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("recordId") recordId: RequestBody,
        @Part("itemName") itemName: RequestBody,
        @Part("photoOrder") photoOrder: RequestBody,
        @Part("uploadedBy") uploadedBy: RequestBody
    ): Response<PhotoUploadResponse>

    /** 获取月度照片列表 */
    @GET("/api/Inspection/photos/monthly")
    suspend fun getMonthlyPhotos(
        @Query("deviceModel") deviceModel: String,
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<List<MonthlyPhoto>>

    /** 删除照片 */
    @DELETE("/api/Inspection/photos/{photoId}")
    suspend fun deletePhoto(
        @Path("photoId") photoId: Int,
        @Query("operatorId") operatorId: String
    ): Response<SubmitResponse>
}
